package com.pulse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GameEventLabelPolicyTest {

    @Test
    void 보호_이벤트_라벨을_반환한다() {
        assertThat(GameEventLabelPolicy.protectedLabel(
                GameEvent.SPOILER_PROTECTED_SAFE, "pressure_bases_loaded")).isEqualTo("만루 승부");
        assertThat(GameEventLabelPolicy.protectedLabel(
                GameEvent.SPOILER_PROTECTED_SAFE, "hard_contact")).isEqualTo("강한 타구");
    }

    @Test
    void 공개_이벤트와_보호_이벤트의_공개_라벨을_반환한다() {
        assertThat(GameEventLabelPolicy.revealedLabel(
                GameEvent.SPOILER_REVEALED_ONLY, "home_run")).isEqualTo("홈런");
        assertThat(GameEventLabelPolicy.revealedLabel(
                GameEvent.SPOILER_PROTECTED_SAFE, "long_at_bat")).isEqualTo("긴 접전 승부");
    }

    @Test
    void 스포일러_등급과_이벤트_유형이_미지값이면_차단한다() {
        assertThat(GameEventLabelPolicy.protectedLabel(
                GameEvent.SPOILER_REVEALED_ONLY, "pressure_bases_loaded")).isNull();
        assertThat(GameEventLabelPolicy.revealedLabel("UNKNOWN", "home_run")).isNull();
        assertThat(GameEventLabelPolicy.revealedLabel(
                GameEvent.SPOILER_REVEALED_ONLY, "unknown_event")).isNull();
        assertThat(GameEventLabelPolicy.protectedLabel(
                GameEvent.SPOILER_PROTECTED_SAFE, null)).isNull();
    }
}
