package com.pulse.replay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.common.client.BdlDtos.BdlPlay;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import com.pulse.poller.PlayerStubWriter;
import com.pulse.replay.S3RawArchiveClient.RawEnvelope;
import com.pulse.gameprocessing.application.ScoreRecalculationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("replay")
@RequiredArgsConstructor
@Slf4j
public class S3ReplayDataLoader implements ApplicationRunner {

    private final S3RawArchiveClient archiveClient;
    private final ReplayProperties replayProperties;
    private final ObjectMapper objectMapper;
    private final GameRepository gameRepository;
    private final PlayRepository playRepository;
    private final PlayerStubWriter playerStubWriter;
    private final ScoreRecalculationService scoreRecalculationService;

    @Override
    public void run(ApplicationArguments args) {
        int[] gameSnapshots = {0};
        int[] plays = {0};

        int objects = archiveClient.streamReplayObjects(envelope -> {
            if ("/games".equals(envelope.endpoint())) {
                gameSnapshots[0] += ingestGames(envelope);
            } else if ("/plays".equals(envelope.endpoint())) {
                plays[0] += ingestPlays(envelope);
            }
        });

        log.info("S3 replay loaded: gameSnapshots={}, plays={}, objects={}",
                gameSnapshots[0], plays[0], objects);
    }

    private int ingestGames(RawEnvelope envelope) {
        int saved = 0;
        for (JsonNode node : dataNodes(envelope.response())) {
            BdlGame dto = objectMapper.convertValue(node, BdlGame.class);
            if (!matchesConfiguredGame(dto.id())) {
                continue;
            }
            Game game = upsertGame(dto, envelope.observedAt());
            saved++;
            if (game.isLive() || game.isFinal()) {
                scoreRecalculationService.recalculate(game.getId(), envelope.observedAt());
            }
        }
        return saved;
    }

    private int ingestPlays(RawEnvelope envelope) {
        Long gameId = gameIdFrom(envelope.params(), envelope.key());
        if (gameId == null || !matchesConfiguredGame(gameId)) {
            return 0;
        }

        int saved = 0;
        for (JsonNode node : dataNodes(envelope.response())) {
            BdlPlay dto = objectMapper.convertValue(node, BdlPlay.class);
            if (dto.order() == null || playRepository.existsByGameIdAndPlayOrder(gameId, dto.order())) {
                continue;
            }
            playerStubWriter.ensurePlayerExists(dto.batterId(), envelope.observedAt());
            playerStubWriter.ensurePlayerExists(dto.pitcherId(), envelope.observedAt());
            playRepository.save(toPlay(gameId, dto, envelope));
            saved++;
        }

        if (saved > 0) {
            scoreRecalculationService.recalculate(gameId, envelope.observedAt());
        }
        return saved;
    }

    private Game upsertGame(BdlGame dto, Instant observedAt) {
        Game game = gameRepository.findById(dto.id()).orElseGet(() -> {
            Game created = new Game();
            created.setId(dto.id());
            return created;
        });

        game.setStatus(dto.status());
        game.setPeriod(dto.period());
        if (dto.date() != null && !dto.date().isBlank()) {
            game.setStartTime(Instant.parse(dto.date()));
        }
        if (dto.homeTeam() != null) {
            game.setHomeTeamId(dto.homeTeam().id());
            game.setHomeTeamName(dto.homeTeam().displayName());
            game.setHomeTeamAbbr(dto.homeTeam().abbreviation());
        }
        if (dto.awayTeam() != null) {
            game.setAwayTeamId(dto.awayTeam().id());
            game.setAwayTeamName(dto.awayTeam().displayName());
            game.setAwayTeamAbbr(dto.awayTeam().abbreviation());
        }
        if (dto.homeTeamData() != null) {
            game.setHomeRuns(dto.homeTeamData().runs());
            game.setHomeInningScores(dto.homeTeamData().inningScores());
        }
        if (dto.awayTeamData() != null) {
            game.setAwayRuns(dto.awayTeamData().runs());
            game.setAwayInningScores(dto.awayTeamData().inningScores());
        }
        game.setUpdatedAt(observedAt);
        return gameRepository.save(game);
    }

    private static Play toPlay(long gameId, BdlPlay dto, RawEnvelope envelope) {
        Play play = new Play();
        play.setGameId(gameId);
        play.setPlayOrder(dto.order());
        play.setType(dto.type());
        play.setInning(dto.inning());
        play.setInningType(dto.inningType());
        play.setText(dto.text());
        play.setHomeScore(dto.homeScore());
        play.setAwayScore(dto.awayScore());
        play.setScoringPlay(dto.scoringPlay());
        play.setScoreValue(dto.scoreValue());
        play.setOuts(dto.outs());
        play.setBalls(dto.balls());
        play.setStrikes(dto.strikes());
        play.setBatterId(dto.batterId());
        play.setPitcherId(dto.pitcherId());
        play.setFetchedAt(envelope.observedAt());
        play.setBackfilled(envelope.backfilled());
        play.setSource(envelope.backfilled() ? "S3_BACKFILL" : "S3_LIVE_ARCHIVE");
        return play;
    }

    private List<JsonNode> dataNodes(JsonNode response) {
        JsonNode data = response == null ? null : response.path("data");
        if (data != null && data.isArray()) {
            return iterableToList(data);
        }
        if (response != null && response.isArray()) {
            return iterableToList(response);
        }
        return List.of();
    }

    private boolean matchesConfiguredGame(long gameId) {
        return replayProperties.gameId() == null || Objects.equals(replayProperties.gameId(), gameId);
    }

    private static Long gameIdFrom(JsonNode params, String key) {
        if (params != null) {
            JsonNode value = params.path("game_id");
            if (value.canConvertToLong()) {
                return value.asLong();
            }
        }

        String marker = "game_id=";
        int start = key.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int valueStart = start + marker.length();
        int valueEnd = key.indexOf('/', valueStart);
        String raw = valueEnd < 0 ? key.substring(valueStart) : key.substring(valueStart, valueEnd);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<JsonNode> iterableToList(JsonNode array) {
        List<JsonNode> nodes = new ArrayList<>();
        array.forEach(nodes::add);
        return nodes;
    }
}
