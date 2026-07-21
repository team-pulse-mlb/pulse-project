package com.pulse.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import com.pulse.scorer.TestScoringProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class PregameScoreFormulasTest {

    private final PregameScoreFormulas formulas = new PregameScoreFormulas(TestScoringProperties.version5());

    @Test
    void 배당_확률_중앙값이_오할이면_근접도_만점이다() {
        assertThat(formulas.closenessFromProbabilities(List.of(0.4, 0.5, 0.6))).isEqualTo(30);
    }

    @Test
    void 배당_확률_중앙값이_허용_범위_밖이면_근접도는_영점이다() {
        assertThat(formulas.closenessFromProbabilities(List.of(0.8))).isZero();
    }

    @Test
    void 승률_차이에_따라_근접도를_계산한다() {
        assertThat(formulas.closenessFromWinPercent(0.6, 0.525)).isCloseTo(15, offset(0.000_001));
        assertThat(formulas.closenessFromWinPercent(0.7, 0.5)).isZero();
    }

    @Test
    void 경쟁권_입력_조합에_따라_점수를_계산한다() {
        assertThat(formulas.contending(null)).isNull();
        assertThat(formulas.contentionScore(null, true)).isZero();
        assertThat(formulas.contentionScore(true, false)).isEqualTo(15);
        assertThat(formulas.contentionScore(true, true)).isEqualTo(30);
        assertThat(formulas.contentionScore(false, false)).isZero();
    }

    @Test
    void 경쟁권_경계값을_포함한다() {
        assertThat(formulas.contending(10.0)).isTrue();
        assertThat(formulas.contending(90.0)).isTrue();
        assertThat(formulas.contending(9.999)).isFalse();
        assertThat(formulas.contending(90.001)).isFalse();
    }

    @Test
    void 선발_방어율_점수를_영점과_최댓값_사이로_제한한다() {
        assertThat(formulas.starterScoreFromEra(2.6)).isCloseTo(20, offset(0.000_001));
        assertThat(formulas.starterScoreFromEra(4.6)).isZero();
        assertThat(formulas.starterScoreFromEra(5.0)).isZero();
    }

    @Test
    void 정규화_확률은_배당이_없으면_반환하지_않는다() {
        assertThat(formulas.normalizedHomeProbability(null, -110)).isEmpty();
        assertThat(formulas.normalizedHomeProbability(-110, null)).isEmpty();
    }

    @Test
    void 정규화_확률은_내재_확률_합계가_영점이면_반환하지_않는다() {
        assertThat(formulas.normalizeHomeProbability(0, 0)).isEmpty();
    }

    @Test
    void 정규화_확률은_양팀_내재_확률의_합으로_홈_확률을_계산한다() {
        assertThat(formulas.normalizedHomeProbability(-110, -110)).contains(0.5);
    }
}
