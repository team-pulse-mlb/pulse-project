package com.pulse.common.config;

import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.scorer.RankingService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevFixtureDataLoader implements ApplicationRunner {

    public static final long LIVE_GAME_ID = 900001L;
    public static final long SCHEDULED_GAME_ID = 900002L;
    public static final long FINAL_GAME_ID = 900003L;

    private final GameRepository gameRepository;
    private final PlayRepository playRepository;
    private final WatchScoreRepository watchScoreRepository;
    private final RankingService rankingService;
    private final ScoringProperties scoringProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        saveLiveFixture();
        saveScheduledFixture();
        saveFinalFixture();
    }

    private void saveLiveFixture() {
        Game game = game(
                LIVE_GAME_ID,
                Game.STATUS_IN_PROGRESS,
                Instant.parse("2026-07-02T10:00:00Z"),
                8,
                101L,
                "Los Angeles Dodgers",
                "LAD",
                102L,
                "New York Yankees",
                "NYY",
                3,
                2
        );
        game.setHomeInningScores(List.of(0, 0, 1, 0, 0, 1, 1));
        game.setAwayInningScores(List.of(0, 1, 0, 0, 0, 1, 0));
        gameRepository.save(game);

        savePlayIfMissing(LIVE_GAME_ID, 1001L, 7, "BOTTOM", "Single to center field", 3, 2, false, null, 1, 2, 1);
        savePlayIfMissing(LIVE_GAME_ID, 1002L, 8, "TOP", "Double to left field, runner advances to third", 3, 2, false, null, 1, 1, 0);
        savePlayIfMissing(LIVE_GAME_ID, 1003L, 8, "TOP", "Groundout, runner scores", 3, 2, true, 1, 2, 0, 1);
        saveWatchScoreIfMissing(
                LIVE_GAME_ID,
                82.0,
                82.0,
                orderedSignals(Map.of(
                        "late_or_extra", 15.0,
                        "score_gap", 25.0,
                        "recent_score", 18.0,
                        "lead_change", 12.0,
                        "big_inning", 12.0
                )),
                List.of("후반 긴장 구간", "접전 흐름", "최근 점수 변화", "흐름 급변", "한 이닝 흐름 집중"),
                Instant.parse("2026-07-02T10:35:00Z")
        );
        rankingService.updateLive(LIVE_GAME_ID, 82.0);
    }

    private void saveScheduledFixture() {
        Game game = game(
                SCHEDULED_GAME_ID,
                Game.STATUS_SCHEDULED,
                Instant.parse("2026-07-03T01:10:00Z"),
                null,
                201L,
                "New York Mets",
                "NYM",
                202L,
                "Atlanta Braves",
                "ATL",
                null,
                null
        );
        gameRepository.save(game);

        saveWatchScoreIfMissing(
                SCHEDULED_GAME_ID,
                64.0,
                64.0,
                orderedSignals(Map.of("pregame_interest", 64.0)),
                List.of("기대 경기"),
                Instant.parse("2026-07-02T09:00:00Z")
        );
    }

    private void saveFinalFixture() {
        Game game = game(
                FINAL_GAME_ID,
                Game.STATUS_FINAL,
                Instant.parse("2026-07-01T23:05:00Z"),
                9,
                301L,
                "Chicago Cubs",
                "CHC",
                302L,
                "St. Louis Cardinals",
                "STL",
                5,
                4
        );
        game.setHomeInningScores(List.of(0, 0, 1, 0, 2, 0, 1, 1, 0));
        game.setAwayInningScores(List.of(0, 1, 0, 0, 0, 2, 0, 1, 0));
        gameRepository.save(game);

        savePlayIfMissing(FINAL_GAME_ID, 2001L, 8, "BOTTOM", "Solo home run to left field", 5, 4, true, 1, 0, 0, 0);
        savePlayIfMissing(FINAL_GAME_ID, 2002L, 9, "TOP", "Strikeout swinging", 5, 4, false, null, 1, 3, 2);
        savePlayIfMissing(FINAL_GAME_ID, 2003L, 9, "TOP", "Flyout to right field", 5, 4, false, null, 2, 1, 1);
        saveWatchScoreIfMissing(
                FINAL_GAME_ID,
                78.0,
                78.0,
                orderedSignals(Map.of(
                        "replay_segment", 35.0,
                        "score_gap", 25.0,
                        "late_or_extra", 18.0
                )),
                List.of("다시보기 추천 구간", "접전 흐름", "후반 긴장 구간"),
                Instant.parse("2026-07-02T00:10:00Z")
        );
    }

    private Game game(
            Long id,
            String status,
            Instant startTime,
            Integer period,
            Long homeTeamId,
            String homeTeamName,
            String homeTeamAbbr,
            Long awayTeamId,
            String awayTeamName,
            String awayTeamAbbr,
            Integer homeRuns,
            Integer awayRuns
    ) {
        Game game = gameRepository.findById(id).orElseGet(Game::new);
        game.setId(id);
        game.setStatus(status);
        game.setStartTime(startTime);
        game.setPeriod(period);
        game.setHomeTeamId(homeTeamId);
        game.setHomeTeamName(homeTeamName);
        game.setHomeTeamAbbr(homeTeamAbbr);
        game.setAwayTeamId(awayTeamId);
        game.setAwayTeamName(awayTeamName);
        game.setAwayTeamAbbr(awayTeamAbbr);
        game.setHomeRuns(homeRuns);
        game.setAwayRuns(awayRuns);
        game.setUpdatedAt(Instant.now());
        return game;
    }

    private void savePlayIfMissing(
            Long gameId,
            Long playOrder,
            Integer inning,
            String inningType,
            String text,
            Integer homeScore,
            Integer awayScore,
            Boolean scoringPlay,
            Integer scoreValue,
            Integer outs,
            Integer balls,
            Integer strikes
    ) {
        if (playRepository.existsByGameIdAndPlayOrder(gameId, playOrder)) {
            return;
        }
        Play play = new Play();
        play.setGameId(gameId);
        play.setPlayOrder(playOrder);
        play.setType("Play");
        play.setInning(inning);
        play.setInningType(inningType);
        play.setText(text);
        play.setHomeScore(homeScore);
        play.setAwayScore(awayScore);
        play.setScoringPlay(scoringPlay);
        play.setScoreValue(scoreValue);
        play.setOuts(outs);
        play.setBalls(balls);
        play.setStrikes(strikes);
        play.setFetchedAt(Instant.now());
        playRepository.save(play);
    }

    private void saveWatchScoreIfMissing(
            Long gameId,
            double baseScore,
            double watchScore,
            Map<String, Double> signals,
            List<String> reasonTags,
            Instant createdAt
    ) {
        if (watchScoreRepository.findTopByGameIdOrderByCreatedAtDesc(gameId).isPresent()) {
            return;
        }
        WatchScore score = new WatchScore();
        score.setGameId(gameId);
        score.setBaseScore(baseScore);
        score.setWatchScore(watchScore);
        score.setSignals(signals);
        score.setReasonTags(reasonTags);
        score.setConfigVersion(scoringProperties.version());
        score.setCreatedAt(createdAt);
        watchScoreRepository.save(score);
    }

    private static Map<String, Double> orderedSignals(Map<String, Double> signals) {
        Map<String, Double> ordered = new LinkedHashMap<>();
        signals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> ordered.put(e.getKey(), e.getValue()));
        return ordered;
    }
}
