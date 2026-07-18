package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.Play;
import java.time.Instant;
import java.util.List;
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
    @DisplayName("리드 변경 직후에는 보너스를 그대로 적용한다")
    void leadChangeImmediatelyAddsFullBonus() {
        Game game = game(8, 4, 5);
        // 4-3 (홈 리드) → 4-5 (원정 리드): 리드 변경
        List<Play> withLeadChange = List.of(
                play(8, 4, 3, now.minusSeconds(300)),
                scoringPlay(8, 4, 5, now));

        double changed = signal(game, withLeadChange, "lead_change");

        assertThat(changed).isEqualTo(testProps().leadChange().bonus());
    }

    @Test
    @DisplayName("리드 변경 후 tau가 지나면 보너스가 약 0.368배로 감쇠한다")
    void leadChangeDecaysAfterTau() {
        Game game = game(8, 4, 5);
        List<Play> plays = List.of(
                play(8, 4, 3, now.minusSeconds(600)),
                scoringPlay(8, 4, 5, now.minusSeconds(300)));

        double changed = signal(game, plays, "lead_change");
        double expected = testProps().leadChange().bonus() * Math.exp(-1);

        assertThat(changed).isCloseTo(expected, within(0.01));
    }

    @Test
    @DisplayName("가장 최근 리드 변경 시각을 기준으로 감쇠한다")
    void leadChangeUsesMostRecentChange() {
        Game game = game(8, 5, 4);
        List<Play> plays = List.of(
                play(8, 3, 2, now.minusSeconds(600)),
                scoringPlay(8, 3, 4, now.minusSeconds(300)),
                scoringPlay(8, 5, 4, now));

        double changed = signal(game, plays, "lead_change");

        assertThat(changed).isEqualTo(testProps().leadChange().bonus());
    }

    @Test
    @DisplayName("리드 변경이 탐색 윈도 밖이면 보너스를 적용하지 않는다")
    void leadChangeOutsideWindowAddsNoBonus() {
        Game game = game(8, 3, 4);
        Play[] allPlays = new Play[testProps().leadChange().windowPlays() + 2];
        allPlays[0] = play(8, 3, 2, now.minusSeconds(1400));
        allPlays[1] = scoringPlay(8, 3, 4, now.minusSeconds(1300));
        for (int index = 2; index < allPlays.length; index++) {
            allPlays[index] = play(8, 3, 4, now.minusSeconds(1200 - index));
        }
        List<Play> recentWindow = List.of(allPlays)
                .subList(allPlays.length - testProps().leadChange().windowPlays(), allPlays.length);

        ScoreCalculator.Result result = calculator.calculate(game, recentWindow, null, -1, now);

        assertThat(result.signals().get("lead_change")).isZero();
    }

    @Test
    @DisplayName("리드 변경 시각이 없으면 감쇠 없이 보너스를 적용한다")
    void leadChangeWithoutFetchedAtAddsFullBonus() {
        Game game = game(8, 4, 5);
        // 득점 플래그 없이 점수 역전만 담은 play로 리드 변경 감쇠 방어 로직만 검증한다.
        Play changedLeader = play(8, 4, 5, now);
        changedLeader.setFetchedAt(null);

        ScoreCalculator.Result result = calculator.calculate(
                game,
                List.of(play(8, 4, 3, now.minusSeconds(60)), changedLeader),
                null,
                0,
                now);

        assertThat(result.signals().get("lead_change")).isEqualTo(testProps().leadChange().bonus());
    }

    @Test
    @DisplayName("같은 이닝의 초와 말 득점은 빅이닝으로 합산하지 않는다")
    void bigInningSeparatesInningTypes() {
        Game game = game(6, 3, 2);
        Play topScore = scoringPlay(6, 1, 0, now.minusSeconds(120));
        topScore.setInningType("TOP");
        Play bottomScore = scoringPlay(6, 1, 1, now.minusSeconds(60));
        bottomScore.setInningType("BOTTOM");

        assertThat(signal(game, List.of(topScore, bottomScore), "big_inning")).isZero();
    }

    @Test
    @DisplayName("같은 하프이닝에 득점이 집중되면 빅이닝이 발동한다")
    void bigInningCountsSameHalfInning() {
        Game game = game(6, 3, 2);
        Play first = scoringPlay(6, 0, 1, now.minusSeconds(120));
        first.setInningType("TOP");
        Play second = scoringPlay(6, 0, 2, now.minusSeconds(60));
        second.setInningType("TOP");

        assertThat(signal(game, List.of(first, second), "big_inning")).isPositive();
    }

    @Test
    @DisplayName("윈도 직전 리더를 시드로 사용해 경계의 역전을 감지한다")
    void leadChangeUsesSeedLeaderAtWindowBoundary() {
        Game game = game(8, 3, 4);
        Play changedLeader = scoringPlay(8, 3, 4, now.minusSeconds(60));

        ScoreCalculator.Result result = calculator.calculate(game, List.of(changedLeader), null, 1, now);

        assertThat(result.signals().get("lead_change")).isPositive();
    }

    @Test
    @DisplayName("시드 리더가 없으면 윈도 내부의 기존 역전 판정을 유지한다")
    void leadChangeKeepsExistingBehaviorWithoutSeed() {
        Game game = game(8, 4, 5);
        List<Play> plays = List.of(
                play(8, 4, 3, now.minusSeconds(120)),
                scoringPlay(8, 4, 5, now.minusSeconds(60))
        );

        ScoreCalculator.Result result = calculator.calculate(game, plays, null, 0, now);

        assertThat(result.signals().get("lead_change")).isPositive();
    }

    @Test
    @DisplayName("풀카운트 2아웃은 카운트 신호 상한을 넘지 않는다")
    void countPressureIsCapped() {
        Game game = game(9, 2, 2);
        ScoreTask.Situation fullCountTwoOuts = ScoreTask.Situation.of(2, 3, 2, false, false, false);

        double pressure = calculator.calculate(game, List.of(), fullCountTwoOuts, now)
                .signals()
                .get("count_pressure");

        assertThat(pressure).isEqualTo(testProps().countPressure().max());
    }

    @Test
    @DisplayName("situation이 없으면 압박과 카운트 신호는 0점이다")
    void nullableSituationHasNoPressureSignals() {
        Game game = game(9, 2, 2);

        ScoreCalculator.Result result = calculator.calculate(game, List.of(), null, now);

        assertThat(result.signals().get("pressure")).isZero();
        assertThat(result.signals().get("count_pressure")).isZero();
    }

    @Test
    @DisplayName("게임 스냅샷이 있으면 엔티티보다 발행 시점 이닝과 점수를 사용한다")
    void gameSnapshotOverridesCurrentGameState() {
        Game game = game(9, 10, 1);
        ScoreTask.GameSnapshot gameSnapshot = new ScoreTask.GameSnapshot(3, 4, 3, false);

        ScoreCalculator.Result result = calculator.calculate(new ScoringInput(
                game, List.of(), null, 0, now, 1.0, 0, gameSnapshot));

        assertThat(result.signals().get("late_or_extra")).isZero();
        assertThat(result.signals().get("score_gap")).isEqualTo(testProps().scoreGap().gap01());
        assertThat(result.signals().get("early_slugfest")).isEqualTo(testProps().earlySlugfest().bonus());
    }

    @Test
    @DisplayName("게임 스냅샷의 null 필드는 엔티티 값으로 대체하지 않는다")
    void nullableGameSnapshotFieldsDoNotFallBackToCurrentGameState() {
        Game game = game(9, 10, 1);
        ScoreTask.GameSnapshot gameSnapshot = new ScoreTask.GameSnapshot(null, null, null, null);

        ScoreCalculator.Result result = calculator.calculate(new ScoringInput(
                game, List.of(), null, 0, now, 1.0, 0, gameSnapshot));

        assertThat(result.signals().get("late_or_extra")).isZero();
        assertThat(result.signals().get("score_gap")).isEqualTo(testProps().scoreGap().gap01());
        assertThat(result.signals().get("early_slugfest")).isZero();
    }

    @Test
    @DisplayName("recent_score의 base-budget은 multiplier 적용 전 예산이라 신호는 예산을 넘을 수 있다")
    void recentScoreBaseBudgetIsPreMultiplierBudget() {
        // 동점(gap-0, multiplier 2.0) 상황에서 방금 나온 대량 득점으로 예산을 소진시킨다.
        Game game = game(7, 5, 5);
        Play bigScore = scoringPlay(7, 5, 5, now);
        bigScore.setScoreValue(5); // 5득점 × per-run 6 = 30 > base-budget 15 → base는 15로 제한

        double recent = signal(game, List.of(bigScore), "recent_score");
        int baseBudget = testProps().recentScore().baseBudget();
        double maxMultiplier = testProps().recentScore().multiplierFor(0);

        // 예산은 multiplier 적용 전 기준: 신호는 예산을 넘어 예산×최대배수까지 커질 수 있다.
        assertThat(recent).isGreaterThan(baseBudget);
        assertThat(recent).isLessThanOrEqualTo(baseBudget * maxMultiplier);
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

    /** 현재 scoring.yml과 동일한 테스트 상수 */
    private static ScoringProperties testProps() {
        return TestScoringProperties.version5();
    }
}
