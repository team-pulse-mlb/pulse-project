package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.common.config.ScoringProperties;
import com.pulse.domain.Game;
import com.pulse.domain.Play;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 불변 조건 테스트. 상수(scoring.yml)를 조정해도 상식적인 점수 순서가
 * 깨지지 않는지 모든 PR에서 검증한다.
 */
class ScoreCalculatorTest {

    private final ScoreCalculator calculator = new ScoreCalculator(testProps());
    private final Instant now = Instant.parse("2026-07-02T03:00:00Z");

    @Test
    @DisplayName("9회 1점 차 경기는 5회 7점 차 경기보다 점수가 높다")
    void lateCloseGameBeatsMidBlowout() {
        double lateClose = baseScore(game(9, 3, 2), List.of());
        double midBlowout = baseScore(game(5, 9, 2), List.of());

        assertThat(lateClose).isGreaterThan(midBlowout);
    }

    @Test
    @DisplayName("연장 동점 경기는 이론상 어떤 4점 차 이상 경기보다 점수가 높다")
    void extraInningTieBeatsAnyBlowout() {
        double extraTie = baseScore(game(11, 4, 4), List.of());
        double blowout = baseScore(game(9, 10, 1), List.of());

        assertThat(extraTie).isGreaterThan(blowout);
    }

    @Test
    @DisplayName("방금 나온 득점은 30분 지난 득점보다 최근 득점 기여가 크다")
    void recentScoreDecaysOverTime() {
        Game game = game(7, 3, 3);
        Play freshScore = scoringPlay(7, 3, 3, now.minusSeconds(10));
        Play staleScore = scoringPlay(7, 3, 3, now.minusSeconds(1800));

        double fresh = signal(game, List.of(freshScore), "recent_score");
        double stale = signal(game, List.of(staleScore), "recent_score");

        assertThat(fresh).isGreaterThan(stale);
        assertThat(stale).isLessThan(1.0); // 30분 경과 시 사실상 소멸
    }

    @Test
    @DisplayName("리드 변경이 있으면 없을 때보다 점수가 높다")
    void leadChangeAddsBonus() {
        Game game = game(8, 4, 5);
        // 4-3 (홈 리드) → 4-5 (원정 리드): 리드 변경
        List<Play> withLeadChange = List.of(
                play(8, 4, 3, now.minusSeconds(300)),
                scoringPlay(8, 4, 5, now.minusSeconds(60)));
        // 계속 원정 리드
        List<Play> withoutLeadChange = List.of(
                play(8, 3, 5, now.minusSeconds(300)),
                play(8, 3, 5, now.minusSeconds(60)));

        double changed = signal(game, withLeadChange, "lead_change");
        double unchanged = signal(game, withoutLeadChange, "lead_change");

        assertThat(changed).isGreaterThan(unchanged);
        assertThat(unchanged).isZero();
    }

    @Test
    @DisplayName("풀카운트 2아웃은 카운트 신호 상한을 넘지 않는다")
    void countPressureIsCapped() {
        Game game = game(9, 2, 2);
        Play fullCountTwoOuts = play(9, 2, 2, now.minusSeconds(5));
        fullCountTwoOuts.setBalls(3);
        fullCountTwoOuts.setStrikes(2);
        fullCountTwoOuts.setOuts(2);

        double pressure = signal(game, List.of(fullCountTwoOuts), "count_pressure");

        assertThat(pressure).isEqualTo(testProps().countPressure().max());
    }

    @Test
    @DisplayName("watch_score는 어떤 상황에서도 0~100을 벗어나지 않는다")
    void watchScoreIsClamped() {
        assertThat(calculator.clampWatchScore(-5)).isZero();
        assertThat(calculator.clampWatchScore(73)).isEqualTo(73);
        assertThat(calculator.clampWatchScore(140)).isEqualTo(100);
    }

    // --- helpers ---

    private double baseScore(Game game, List<Play> plays) {
        return calculator.calculate(game, plays, now).baseScore();
    }

    private double signal(Game game, List<Play> plays, String name) {
        return calculator.calculate(game, plays, now).signals().get(name);
    }

    private Game game(int period, int homeRuns, int awayRuns) {
        Game game = new Game();
        game.setId(1L);
        game.setStatus(Game.STATUS_IN_PROGRESS);
        game.setPeriod(period);
        game.setHomeRuns(homeRuns);
        game.setAwayRuns(awayRuns);
        return game;
    }

    private Play play(int inning, int homeScore, int awayScore, Instant fetchedAt) {
        Play play = new Play();
        play.setGameId(1L);
        play.setPlayOrder(fetchedAt.toEpochMilli());
        play.setType("Play Result");
        play.setInning(inning);
        play.setHomeScore(homeScore);
        play.setAwayScore(awayScore);
        play.setScoringPlay(false);
        play.setFetchedAt(fetchedAt);
        return play;
    }

    private Play scoringPlay(int inning, int homeScore, int awayScore, Instant fetchedAt) {
        Play play = play(inning, homeScore, awayScore, fetchedAt);
        play.setScoringPlay(true);
        play.setScoreValue(1);
        return play;
    }

    /** scoring.yml version 2와 동일한 시작 상수 */
    private static ScoringProperties testProps() {
        return new ScoringProperties(
                2,
                new ScoringProperties.LateInning(6, 12, 18),
                new ScoringProperties.ScoreGap(15, 9, 3),
                new ScoringProperties.RecentScore(6, 15, 180,
                        Map.of("gap-0", 2.0, "gap-1", 1.5, "gap-2", 1.2, "default", 1.0)),
                new ScoringProperties.LeadChange(9, 12),
                new ScoringProperties.BigInning(9, 2),
                new ScoringProperties.CountPressure(3, 3, 5),
                new ScoringProperties.EarlySlugfest(5, 3, 7),
                new ScoringProperties.Importance(0.9, 1.15),
                10,
                15,
                new ScoringProperties.Detail(100, 100, 2.0, 10, 8),
                new ScoringProperties.Thresholds(85, 70, 15, 5, 15, 70, 20, 60, 50)
        );
    }
}
