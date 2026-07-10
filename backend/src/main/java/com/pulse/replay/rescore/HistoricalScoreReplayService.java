package com.pulse.replay.rescore;

import com.pulse.common.config.ScoringProperties;
import com.pulse.domain.Game;
import com.pulse.domain.Play;
import com.pulse.scorer.ReasonTags;
import com.pulse.scorer.ScoreCalculator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("rescore")
@RequiredArgsConstructor
@Slf4j
class HistoricalScoreReplayService {

    private final RescoreJdbcRepository repository;
    private final ScoreCalculator calculator;
    private final ScoringProperties scoringProperties;

    void replayAll() {
        List<Long> gameIds = repository.gameIdsWithPlays();

        int totalWatchScores = 0;
        for (Long gameId : gameIds) {
            GameReplayResult result = replayGame(gameId);
            totalWatchScores += result.insertedWatchScores();
            log.info("scorer 재생 완료: gameId={}, cycles={}, watchScoresInserted={}",
                    gameId,
                    result.scoreCycles(),
                    result.insertedWatchScores());
        }

        log.info("scorer 재생 전체 완료: games={}, watchScoresInserted={}",
                gameIds.size(),
                totalWatchScores);
    }

    private GameReplayResult replayGame(Long gameId) {
        List<RescorePlayRow> rows = repository.playsForGame(gameId);
        List<RescorePlayRow> observedPlays = new ArrayList<>();

        int insertedWatchScores = 0;
        int scoreCycles = 0;
        int index = 0;
        while (index < rows.size()) {
            Instant observedAt = rows.get(index).observedAt();
            if (observedAt == null) {
                index++;
                continue;
            }

            int nextIndex = index;
            while (nextIndex < rows.size() && observedAt.equals(rows.get(nextIndex).observedAt())) {
                observedPlays.add(rows.get(nextIndex));
                nextIndex++;
            }
            observedPlays.sort(Comparator.comparing(RescorePlayRow::playOrder));

            RescorePlayRow latest = observedPlays.get(observedPlays.size() - 1);
            List<Play> recentPlays = recentPlays(observedPlays);
            ScoreCalculator.Result result = calculator.calculate(gameFrom(latest), recentPlays, observedAt);
            int baseScore = roundScore(result.baseScore());
            int watchScore = roundScore(calculator.clampWatchScore(result.baseScore()));
            List<String> tags = ReasonTags.from(result.signals());

            insertedWatchScores += repository.insertWatchScore(new RescoreWatchScoreRow(
                    gameId,
                    observedAt,
                    latest.playOrder(),
                    latest.inning(),
                    latest.inningType(),
                    baseScore,
                    watchScore,
                    result.signals(),
                    tags,
                    latest.backfilledValue(),
                    latest.sourceValue()));
            scoreCycles++;

            index = nextIndex;
        }

        return new GameReplayResult(
                scoreCycles,
                insertedWatchScores);
    }

    private List<Play> recentPlays(List<RescorePlayRow> observedPlays) {
        int fromIndex = Math.max(0, observedPlays.size() - scoringProperties.leadChange().windowPlays());
        return observedPlays.subList(fromIndex, observedPlays.size()).stream()
                .map(this::playFrom)
                .toList();
    }

    private Game gameFrom(RescorePlayRow latest) {
        Game game = new Game();
        game.setId(latest.gameId());
        game.setStatus(Game.STATUS_IN_PROGRESS);
        game.setPeriod(latest.inning());
        game.setHomeRuns(latest.homeScore());
        game.setAwayRuns(latest.awayScore());
        return game;
    }

    private Play playFrom(RescorePlayRow row) {
        Play play = new Play();
        play.setGameId(row.gameId());
        play.setPlayOrder(row.playOrder());
        play.setType(row.type());
        play.setInning(row.inning());
        play.setInningType(row.inningType());
        play.setHomeScore(row.homeScore());
        play.setAwayScore(row.awayScore());
        play.setScoringPlay(row.scoringPlay());
        play.setScoreValue(row.scoreValue());
        play.setOuts(row.outs());
        play.setBalls(row.balls());
        play.setStrikes(row.strikes());
        play.setFetchedAt(row.observedAt());
        play.setSource(row.sourceValue());
        play.setBackfilled(row.backfilled());
        return play;
    }

    private static int roundScore(double score) {
        return (int) Math.round(score);
    }

    private record GameReplayResult(
            int scoreCycles,
            int insertedWatchScores
    ) {
    }
}
