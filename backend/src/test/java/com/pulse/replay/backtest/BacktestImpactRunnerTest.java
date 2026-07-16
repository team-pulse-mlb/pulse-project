package com.pulse.replay.backtest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BacktestImpactRunnerTest {

    @Test
    @DisplayName("play가 있는 대상 경기가 없으면 리포트를 생성하지 않는다")
    void rejectsEmptyGames() {
        assertThatThrownBy(() -> BacktestImpactRunner.requireGames(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("백테스트 기간에 play가 있는 경기가 없습니다.");
    }
}
