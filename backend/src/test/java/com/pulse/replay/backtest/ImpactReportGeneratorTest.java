package com.pulse.replay.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImpactReportGeneratorTest {

    @Test
    @DisplayName("기준 알림이 0건이면 후보 알림을 절대 상한으로만 판정한다")
    void usesAbsoluteLimitWhenBaselineHasNoAlerts() {
        assertThat(ImpactReportGenerator.exceedsDailyAlertLimit(0, 3, 5, 2)).isFalse();
        assertThat(ImpactReportGenerator.exceedsDailyAlertLimit(0, 6, 5, 2)).isTrue();
    }

    @Test
    @DisplayName("기준 알림이 있으면 후보 알림의 비율 상한도 판정한다")
    void usesRatioLimitWhenBaselineHasAlerts() {
        assertThat(ImpactReportGenerator.exceedsDailyAlertLimit(1, 3, 5, 2)).isTrue();
        assertThat(ImpactReportGenerator.exceedsDailyAlertLimit(2, 3, 5, 2)).isFalse();
    }
}
