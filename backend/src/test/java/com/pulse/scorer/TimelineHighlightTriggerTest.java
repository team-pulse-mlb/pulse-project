package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.WatchScoreRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimelineHighlightTriggerTest {

    private static final long GAME_ID = 5059041L;
    private static final Instant NOW = Instant.parse("2026-07-10T05:00:00Z");
    private static final ScoringProperties.Highlight ENABLED =
            new ScoringProperties.Highlight(true, 40, 12, 6, 8, 8);

    private final GameEventRepository gameEventRepository = mock(GameEventRepository.class);
    private final WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);
    private final AiGenerationTrigger aiGenerationTrigger = mock(AiGenerationTrigger.class);

    private TimelineHighlightTrigger trigger(ScoringProperties.Highlight highlight) {
        return new TimelineHighlightTrigger(
                gameEventRepository,
                watchScoreRepository,
                aiGenerationTrigger,
                new AfterCommitExecutor(),
                TestScoringProperties.version5(highlight)
        );
    }

    private void armRise() {
        when(gameEventRepository.existsByGameIdAndTimelineHighlightTrueAndObservedAtGreaterThanEqual(
                anyLong(), any())).thenReturn(false);
        when(watchScoreRepository.findMinWatchScoreSince(anyLong(), any())).thenReturn(40);
    }

    private GameEvent anchorEvent() {
        GameEvent event = new GameEvent();
        event.setId(91L);
        event.setGameId(GAME_ID);
        event.setEventType("full_count_two_out");
        event.setSpoilerLevel(GameEvent.SPOILER_PROTECTED_SAFE);
        event.setObservedAt(NOW);
        return event;
    }

    @Test
    @DisplayName("비활성화면 아무 것도 하지 않는다")
    void disabledIsNoOp() {
        trigger(new ScoringProperties.Highlight(false, 40, 12, 6, 8, 8))
                .evaluate(GAME_ID, 80, NOW);

        verify(gameEventRepository, never()).save(any());
        verify(aiGenerationTrigger, never()).onGameEventPersisted(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("최소 점수 미만이면 하이라이트를 만들지 않는다")
    void belowMinScoreSkips() {
        trigger(ENABLED).evaluate(GAME_ID, 39, NOW);

        verify(gameEventRepository, never()).save(any());
        verify(aiGenerationTrigger, never()).onGameEventPersisted(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("쿨다운 안에 이미 하이라이트가 있으면 스킵한다")
    void cooldownSkips() {
        when(gameEventRepository.existsByGameIdAndTimelineHighlightTrueAndObservedAtGreaterThanEqual(
                anyLong(), any())).thenReturn(true);

        trigger(ENABLED).evaluate(GAME_ID, 80, NOW);

        verify(gameEventRepository, never()).save(any());
        verify(aiGenerationTrigger, never()).onGameEventPersisted(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("급상승 폭이 부족하면 스킵한다")
    void insufficientRiseSkips() {
        when(gameEventRepository.existsByGameIdAndTimelineHighlightTrueAndObservedAtGreaterThanEqual(
                anyLong(), any())).thenReturn(false);
        // 80 - 70 = 10 < riseScore(12)
        when(watchScoreRepository.findMinWatchScoreSince(anyLong(), any())).thenReturn(70);

        trigger(ENABLED).evaluate(GAME_ID, 80, NOW);

        verify(gameEventRepository, never()).save(any());
        verify(aiGenerationTrigger, never()).onGameEventPersisted(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("급변했지만 보호 이벤트가 없으면 하이라이트를 만들지 않는다")
    void noAnchorSkips() {
        armRise();
        when(gameEventRepository
                .findFirstByGameIdAndSpoilerLevelAndTimelineHighlightFalseAndObservedAtGreaterThanEqualOrderByObservedAtDescIdDesc(
                        anyLong(), anyString(), any()))
                .thenReturn(Optional.empty());

        trigger(ENABLED).evaluate(GAME_ID, 80, NOW);

        verify(gameEventRepository, never()).save(any());
        verify(aiGenerationTrigger, never()).onGameEventPersisted(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("급변 + 보호 이벤트가 있으면 anchor를 하이라이트로 표시하고 보호 문구 생성을 요청한다")
    void marksAnchorAndRequestsCopy() {
        armRise();
        GameEvent anchor = anchorEvent();
        when(gameEventRepository
                .findFirstByGameIdAndSpoilerLevelAndTimelineHighlightFalseAndObservedAtGreaterThanEqualOrderByObservedAtDescIdDesc(
                        anyLong(), anyString(), any()))
                .thenReturn(Optional.of(anchor));

        trigger(ENABLED).evaluate(GAME_ID, 80, NOW);

        assertThat(anchor.isTimelineHighlight()).isTrue();
        verify(gameEventRepository).save(anchor);
        verify(aiGenerationTrigger).onGameEventPersisted(
                eq(GAME_ID), eq(91L), eq(AiGenerationTrigger.MODE_PROTECTED), eq(NOW));
    }
}
