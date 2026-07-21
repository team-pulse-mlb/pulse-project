package com.pulse.replay.rescore;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.Play;
import com.pulse.scoring.ImportanceCalculator;
import com.pulse.scoring.ReasonTags;
import com.pulse.scoring.ScoreCalculator;
import com.pulse.scoring.ScoringInput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private final RescoreProperties rescoreProperties;

    void replayAll() {
        List<Long> gameIds = repository.gameIdsWithPlays();
        if (!rescoreProperties.gameIds().isEmpty()) {
            gameIds = gameIds.stream()
                    .filter(rescoreProperties.gameIds()::contains)
                    .toList();
        }

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
        Game game = repository.gameForScoring(gameId);
        if (game == null) {
            log.warn("점수 재생 대상 경기를 찾을 수 없음: gameId={}", gameId);
            return new GameReplayResult(0, 0);
        }
        List<RescorePlayRow> rows = repository.playsForGame(gameId);
        List<RescorePlayRow> observedPlays = new ArrayList<>();
        double importance = importance(game);

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
            List<RescorePlayRow> observedGroup = new ArrayList<>();
            while (nextIndex < rows.size() && observedAt.equals(rows.get(nextIndex).observedAt())) {
                observedGroup.add(rows.get(nextIndex));
                nextIndex++;
            }
            observedGroup.sort(Comparator.comparing(RescorePlayRow::playOrder));

            if (observedGroup.stream().allMatch(RescorePlayRow::backfilledValue)) {
                for (int groupIndex = 0; groupIndex < observedGroup.size(); groupIndex++) {
                    RescorePlayRow play = observedGroup.get(groupIndex);
                    observedPlays.add(play);
                    observedPlays.sort(Comparator.comparing(RescorePlayRow::playOrder));
                    // 백필 봉투에는 실제 play 시각이 없어 충돌 방지용 합성 시각을 사용한다.
                    Instant computedAt = observedAt.plusSeconds(groupIndex);
                    insertedWatchScores += insertWatchScore(game, importance, computedAt, play, observedPlays);
                    scoreCycles++;
                }
            } else {
                observedPlays.addAll(observedGroup);
                observedPlays.sort(Comparator.comparing(RescorePlayRow::playOrder));
                RescorePlayRow latest = observedPlays.get(observedPlays.size() - 1);
                insertedWatchScores += insertWatchScore(game, importance, observedAt, latest, observedPlays);
                scoreCycles++;
            }

            index = nextIndex;
        }

        return new GameReplayResult(
                scoreCycles,
                insertedWatchScores);
    }

    private int insertWatchScore(
            Game game,
            double importance,
            Instant computedAt,
            RescorePlayRow current,
            List<RescorePlayRow> observedPlays
    ) {
        RecentPlayWindow window = recentPlays(observedPlays);
        List<Play> recentPlays = window.plays();
        ScoreCalculator.Result result = calculator.calculate(new ScoringInput(
                gameFrom(game, current),
                recentPlays,
                situationFrom(recentPlays),
                window.seedLeader(),
                computedAt,
                importance,
                game.getPregameScore() == null ? 0 : game.getPregameScore()));
        int baseScore = roundScore(result.baseScore());
        int watchScore = roundScore(result.watchScore());
        List<String> tags = ReasonTags.from(result.signals(), result.fullCountIncluded());

        return repository.insertWatchScore(new RescoreWatchScoreRow(
                game.getId(),
                computedAt,
                current.playOrder(),
                current.inning(),
                current.inningType(),
                baseScore,
                decimal(result.importanceMultiplier()),
                decimal(result.pregameBonus()),
                watchScore,
                scoringProperties.version(),
                result.signals(),
                tags,
                current.backfilledValue(),
                current.sourceValue()));
    }

    private RecentPlayWindow recentPlays(List<RescorePlayRow> observedPlays) {
        int fromIndex = Math.max(0, observedPlays.size() - scoringProperties.leadChange().windowPlays());
        int seedLeader = fromIndex == 0 ? 0 : leaderOf(observedPlays.get(fromIndex - 1));
        List<Play> plays = observedPlays.subList(fromIndex, observedPlays.size()).stream()
                .map(this::playFrom)
                .toList();
        return new RecentPlayWindow(plays, seedLeader);
    }

    private Game gameFrom(Game base, RescorePlayRow latest) {
        Game game = new Game();
        game.setId(base.getId());
        game.setStatus(base.getStatus());
        game.setPostseason(base.getPostseason());
        game.setHomeTeamId(base.getHomeTeamId());
        game.setAwayTeamId(base.getAwayTeamId());
        game.setPregameScore(base.getPregameScore());
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
        play.setRunnerOnFirst(row.runnerOnFirst());
        play.setRunnerOnSecond(row.runnerOnSecond());
        play.setRunnerOnThird(row.runnerOnThird());
        play.setFetchedAt(row.observedAt());
        play.setSource(row.sourceValue());
        play.setBackfilled(row.backfilled());
        return play;
    }

    private static int roundScore(double score) {
        return (int) Math.round(score);
    }

    private double importance(Game game) {
        if (Boolean.TRUE.equals(game.getPostseason())) {
            return scoringProperties.importance().postseason();
        }
        LocalDate gameDate = game.getStartTime() == null
                ? null
                : game.getStartTime().atOffset(ZoneOffset.UTC).toLocalDate();
        return ImportanceCalculator.multiplier(
                scoringProperties.importance(),
                game.getPostseason(),
                repository.playoffPercentAt(game.getHomeTeamId(), gameDate),
                repository.playoffPercentAt(game.getAwayTeamId(), gameDate));
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static int leaderOf(RescorePlayRow play) {
        if (play.homeScore() == null || play.awayScore() == null) {
            return 0;
        }
        return Integer.signum(play.homeScore() - play.awayScore());
    }

    private static ScoreTask.Situation situationFrom(List<Play> plays) {
        if (plays.isEmpty()) {
            return null;
        }
        Play latest = plays.get(plays.size() - 1);
        return ScoreTask.Situation.of(
                latest.getOuts(),
                latest.getBalls(),
                latest.getStrikes(),
                latest.getRunnerOnFirst(),
                latest.getRunnerOnSecond(),
                latest.getRunnerOnThird()
        );
    }

    private record RecentPlayWindow(List<Play> plays, int seedLeader) {
    }

    private record GameReplayResult(
            int scoreCycles,
            int insertedWatchScores
    ) {
    }
}
