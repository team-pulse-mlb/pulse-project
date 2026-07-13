package com.pulse.poller.simulation;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.pulse.common.client.BaseballDataSource;
import com.pulse.common.client.BdlDtos.*;
import com.pulse.common.time.SlateZone;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 운영 DB의 과거 경기를 현재 시각의 라이브 API 응답처럼 순차 제공한다. */
@Component
@ConditionalOnProperty(prefix = "pulse.simulation", name = "enabled", havingValue = "true")
@Slf4j
public class SimulationBaseballDataSource implements BaseballDataSource {
    private final SimulationProperties properties;
    private final GameRepository gameRepository;
    private final PlayRepository playRepository;
    private final SimulationArchiveLoader archiveLoader;
    private final Clock clock;
    private volatile List<Timeline> timelines;

    @Autowired
    public SimulationBaseballDataSource(SimulationProperties properties, GameRepository gameRepository,
                                        PlayRepository playRepository, SimulationArchiveLoader archiveLoader) {
        this(properties, gameRepository, playRepository, archiveLoader, Clock.systemUTC());
    }

    SimulationBaseballDataSource(SimulationProperties properties, GameRepository gameRepository, PlayRepository playRepository, Clock clock) {
        this(properties, gameRepository, playRepository, null, clock);
    }

    private SimulationBaseballDataSource(SimulationProperties properties, GameRepository gameRepository,
                                         PlayRepository playRepository, SimulationArchiveLoader archiveLoader, Clock clock) {
        this.properties = properties;
        this.gameRepository = gameRepository;
        this.playRepository = playRepository;
        this.archiveLoader = archiveLoader;
        this.clock = clock;
    }

    @Override
    public List<BdlGame> getGames(LocalDate date) {
        List<Timeline> current = timelines();
        Instant now = clock.instant();
        // 홈 슬레이트 버킷과 같은 ET 기준으로 "오늘 노출 대상"을 판단해, UTC·ET 자정 경계에서
        // 카드가 다른 날짜로 새는 문제를 막는다. 폴러 lookback이 ET 오늘을 UTC 날짜로 포함한다.
        if (!LocalDate.ofInstant(now, SlateZone.ID).equals(date)) return List.of();
        return current.stream().map(timeline -> toGame(timeline, now)).toList();
    }

    @Override
    public ListResponse<BdlPlay> getPlays(long gameId, Long cursor) {
        Timeline current = timelines().stream()
                .filter(timeline -> timeline.targetGameId() == gameId)
                .findFirst()
                .orElse(null);
        if (current == null) return emptyPage();
        List<BdlPlay> visible = current.plays().subList(0, current.visiblePlayCount(clock.instant())).stream()
                .filter(play -> cursor == null || play.order() > cursor)
                .toList();
        return new ListResponse<>(visible, new ListResponse.Meta(null, visible.size()));
    }

    @Override
    public PlateAppearancesRaw getPlateAppearancesRaw(long gameId) {
        var response = JsonNodeFactory.instance.objectNode();
        response.putArray("data");
        return new PlateAppearancesRaw(response, List.of());
    }

    @Override public List<BdlPlateAppearance> getPlateAppearances(long gameId) { return List.of(); }
    @Override public List<BdlLineup> getLineups(List<Long> gameIds) { return List.of(); }
    @Override public List<BdlOdds> getOdds(List<Long> gameIds) { return List.of(); }
    @Override public List<BdlStanding> getStandings(int season) { return List.of(); }
    @Override public List<BdlPlayerSeasonStat> getPlayerSeasonStats(int season, List<Long> playerIds) { return List.of(); }
    @Override public List<BdlPlayer> getPlayers(List<Long> playerIds) { return List.of(); }

    private List<Timeline> timelines() {
        if (timelines != null) return timelines;
        synchronized (this) {
            if (timelines == null) timelines = loadTimelines();
            return timelines;
        }
    }

    private List<Timeline> loadTimelines() {
        boolean legacySingleGame = properties.games().isEmpty();
        return properties.resolvedGames().stream()
                .map(spec -> loadTimeline(spec, legacySingleGame))
                .toList();
    }

    private Timeline loadTimeline(SimulationProperties.ResolvedGameSpec spec, boolean legacySingleGame) {
        long sourceGameId = spec.sourceGameId();
        long targetGameId = spec.targetGameId();
        if (gameRepository.existsById(targetGameId)) {
            throw new IllegalStateException("simulation target game already exists; choose a new pulse.simulation.target-game-id");
        }
        var archived = !legacySingleGame || archiveLoader == null
                ? java.util.Optional.<SimulationArchiveLoader.ArchiveGame>empty()
                : archiveLoader.load();
        Game source = archived.map(value -> toGame(value.game())).orElseGet(() -> gameRepository.findById(sourceGameId)
                .orElseThrow(() -> new IllegalStateException("simulation source game not found: " + sourceGameId)));
        List<BdlPlay> plays = archived.map(SimulationArchiveLoader.ArchiveGame::plays)
                .orElseGet(() -> playRepository.findByGameIdOrderByPlayOrderAsc(sourceGameId).stream()
                        .map(SimulationBaseballDataSource::toDto).toList());
        if (plays.isEmpty()) throw new IllegalStateException("simulation source game has no plays: " + sourceGameId);
        Duration offset = spec.startOffset().plus(presetOffset(plays, spec.preset()));
        Instant startedAt = clock.instant().minusNanos((long) (offset.toNanos() / properties.speed()));
        log.info("simulation ready: sourceGameId={}, targetGameId={}, plays={}, speed=x{}, offset={}",
                sourceGameId, targetGameId, plays.size(), properties.speed(), offset);
        return new Timeline(source, targetGameId, plays, startedAt, properties.speed(), properties.playInterval());
    }

    private Duration presetOffset(List<BdlPlay> plays, String preset) {
        if (!"SURGE".equals(preset)) return Duration.ZERO;
        for (int i = 0; i < plays.size(); i++) {
            BdlPlay play = plays.get(i);
            if (Boolean.TRUE.equals(play.scoringPlay()) && play.inning() != null && play.inning() >= 7) {
                return properties.playInterval().multipliedBy(Math.max(0, i - 5));
            }
        }
        return Duration.ZERO;
    }

    private static BdlGame.Team team(Long id, String name, String abbreviation) {
        return new BdlGame.Team(id == null ? -1L : id, name, abbreviation);
    }

    private static int score(Integer value) { return value == null ? 0 : value; }

    private static BdlGame toGame(Timeline timeline, Instant now) {
        if (now.isBefore(timeline.startedAt())) {
            return new BdlGame(
                    timeline.targetGameId(), timeline.startedAt().toString(), Game.STATUS_SCHEDULED, 1,
                    team(timeline.source().getHomeTeamId(), timeline.source().getHomeTeamName(), timeline.source().getHomeTeamAbbr()),
                    team(timeline.source().getAwayTeamId(), timeline.source().getAwayTeamName(), timeline.source().getAwayTeamAbbr()),
                    new BdlGame.TeamData(0, List.of()), new BdlGame.TeamData(0, List.of()), timeline.source().getVenue());
        }
        int visibleCount = timeline.visiblePlayCount(now);
        BdlPlay latest = visibleCount == 0 ? null : timeline.plays().get(visibleCount - 1);
        String status = visibleCount >= timeline.plays().size() ? Game.STATUS_FINAL : Game.STATUS_IN_PROGRESS;
        return new BdlGame(
                timeline.targetGameId(), now.minus(Duration.ofMinutes(1)).toString(), status,
                latest == null ? 1 : latest.inning(),
                team(timeline.source().getHomeTeamId(), timeline.source().getHomeTeamName(), timeline.source().getHomeTeamAbbr()),
                team(timeline.source().getAwayTeamId(), timeline.source().getAwayTeamName(), timeline.source().getAwayTeamAbbr()),
                new BdlGame.TeamData(score(latest == null ? null : latest.homeScore()), List.of()),
                new BdlGame.TeamData(score(latest == null ? null : latest.awayScore()), List.of()),
                timeline.source().getVenue());
    }

    private static BdlPlay toDto(Play play) {
        return new BdlPlay(play.getPlayOrder(), play.getType(), play.getInning(), play.getInningType(), play.getText(),
                play.getHomeScore(), play.getAwayScore(), play.getScoringPlay(), play.getScoreValue(), play.getOuts(),
                play.getBalls(), play.getStrikes(), play.getBatterId(), play.getPitcherId());
    }

    private static Game toGame(BdlGame dto) {
        Game game = new Game();
        game.setId(dto.id());
        game.setStatus(dto.status());
        game.setVenue(dto.venue());
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
        return game;
    }

    private static ListResponse<BdlPlay> emptyPage() {
        return new ListResponse<>(List.of(), new ListResponse.Meta(null, 0));
    }

    private record Timeline(Game source, long targetGameId, List<BdlPlay> plays, Instant startedAt, double speed, Duration playInterval) {
        int visiblePlayCount(Instant now) {
            if (now.isBefore(startedAt)) return 0;
            long elapsedNanos = Math.max(0L, Duration.between(startedAt, now).toNanos());
            long count = (long) ((elapsedNanos * speed) / playInterval.toNanos()) + 1L;
            return (int) Math.min(plays.size(), count);
        }
    }
}
