package com.pulse.gameprocessing.highlight;

import com.pulse.gameprocessing.aicopy.AiGenerationTrigger;
import com.pulse.scoring.TestScoringProperties;
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
import java.util.List;
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
        return protectedEvent(91L, "full_count_two_out", NOW);
    }

    private static GameEvent protectedEvent(long id, String eventType, Instant observedAt) {
        GameEvent event = new GameEvent();
        event.setId(id);
        event.setGameId(GAME_ID);
        event.setEventType(eventType);
        event.setSpoilerLevel(GameEvent.SPOILER_PROTECTED_SAFE);
        event.setObservedAt(observedAt);
        return event;
    }

    private void stubAnchorCandidates(List<GameEvent> candidates) {
        when(gameEventRepository
                .findByGameIdAndSpoilerLevelAndTimelineHighlightFalseAndObservedAtGreaterThanEqualOrderByObservedAtAscIdAsc(
                        anyLong(), anyString(), any()))
                .thenReturn(candidates);
    }

    private void stubLatestHighlightType(String eventType) {
        GameEvent latestHighlight = protectedEvent(1L, eventType, NOW.minusSeconds(3600));
        latestHighlight.setTimelineHighlight(true);
        when(gameEventRepository.findFirstByGameIdAndTimelineHighlightTrueOrderByObservedAtDescIdDesc(GAME_ID))
                .thenReturn(Optional.of(latestHighlight));
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
        stubAnchorCandidates(List.of());

        trigger(ENABLED).evaluate(GAME_ID, 80, NOW);

        verify(gameEventRepository, never()).save(any());
        verify(aiGenerationTrigger, never()).onGameEventPersisted(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("급변 + 보호 이벤트가 있으면 anchor를 하이라이트로 표시하고 보호 문구 생성을 요청한다")
    void marksAnchorAndRequestsCopy() {
        armRise();
        GameEvent anchor = anchorEvent();
        stubAnchorCandidates(List.of(anchor));

        trigger(ENABLED).evaluate(GAME_ID, 80, NOW);

        assertThat(anchor.isTimelineHighlight()).isTrue();
        verify(gameEventRepository).save(anchor);
        verify(aiGenerationTrigger).onGameEventPersisted(
                eq(GAME_ID), eq(91L), eq(AiGenerationTrigger.MODE_PROTECTED), eq(NOW));
    }

    @Test
    @DisplayName("윈도에 여러 유형이 있으면 최근 이벤트보다 정보량이 큰 유형을 anchor로 고른다")
    void prefersHigherInformationTypeOverRecency() {
        armRise();
        GameEvent pressure = protectedEvent(91L, "pressure_bases_loaded", NOW.minusSeconds(300));
        GameEvent hardContact = protectedEvent(92L, "hard_contact", NOW);
        stubAnchorCandidates(List.of(pressure, hardContact));

        trigger(ENABLED).evaluate(GAME_ID, 80, NOW);

        assertThat(pressure.isTimelineHighlight()).isTrue();
        assertThat(hardContact.isTimelineHighlight()).isFalse();
        verify(gameEventRepository).save(pressure);
    }

    @Test
    @DisplayName("직전 하이라이트와 같은 유형은 회피하고 차순위 유형을 고른다")
    void avoidsSameTypeAsLatestHighlight() {
        armRise();
        stubLatestHighlightType("hard_contact");
        GameEvent hardContact = protectedEvent(91L, "hard_contact", NOW);
        GameEvent longAtBat = protectedEvent(92L, "long_at_bat", NOW.minusSeconds(300));
        stubAnchorCandidates(List.of(longAtBat, hardContact));

        trigger(ENABLED).evaluate(GAME_ID, 80, NOW);

        assertThat(longAtBat.isTimelineHighlight()).isTrue();
        assertThat(hardContact.isTimelineHighlight()).isFalse();
        verify(gameEventRepository).save(longAtBat);
    }

    @Test
    @DisplayName("윈도에 직전 하이라이트와 같은 유형뿐이면 그대로 anchor로 허용한다")
    void fallsBackToSameTypeWhenNoAlternative() {
        armRise();
        stubLatestHighlightType("hard_contact");
        GameEvent hardContact = protectedEvent(91L, "hard_contact", NOW);
        stubAnchorCandidates(List.of(hardContact));

        trigger(ENABLED).evaluate(GAME_ID, 80, NOW);

        assertThat(hardContact.isTimelineHighlight()).isTrue();
        verify(gameEventRepository).save(hardContact);
    }

    @Test
    @DisplayName("보호 라벨을 산출할 수 없는 이벤트는 anchor 후보에서 제외한다")
    void excludesUnlabeledEvents() {
        armRise();
        GameEvent unknown = protectedEvent(91L, "unknown_event", NOW);
        stubAnchorCandidates(List.of(unknown));

        trigger(ENABLED).evaluate(GAME_ID, 80, NOW);

        verify(gameEventRepository, never()).save(any());
        verify(aiGenerationTrigger, never()).onGameEventPersisted(anyLong(), anyLong(), anyString(), any());
    }
}
