package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimelineHighlightBackfillTest {

    private static final long GAME_ID = 5059041L;
    private static final Instant START = Instant.parse("2026-07-10T05:00:00Z");
    private static final Instant NOW = Instant.parse("2026-07-10T08:00:00Z");
    private static final ScoringProperties.Highlight ENABLED =
            new ScoringProperties.Highlight(true, 40, 12, 6, 8, 8);

    private final GameEventRepository gameEventRepository = mock(GameEventRepository.class);
    private final WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);
    private final AiGenerationTrigger aiGenerationTrigger = mock(AiGenerationTrigger.class);

    private TimelineHighlightBackfill backfill(ScoringProperties.Highlight highlight) {
        return new TimelineHighlightBackfill(
                gameEventRepository,
                watchScoreRepository,
                aiGenerationTrigger,
                new AfterCommitExecutor(),
                TestScoringProperties.version5(highlight)
        );
    }

    @Test
    @DisplayName("하이라이트가 이미 있으면 아무 것도 하지 않는다")
    void existingHighlightIsNoOp() {
        when(gameEventRepository.existsByGameIdAndTimelineHighlightTrue(GAME_ID)).thenReturn(true);

        int marked = backfill(ENABLED).backfillIfEmpty(GAME_ID, NOW, true);

        assertThat(marked).isZero();
        verify(watchScoreRepository, never()).findByGameIdOrderByComputedAtAsc(anyLong());
        verify(gameEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("최대 점수가 최소 점수 미만이어도 급변 폭을 충족하면 표시한다")
    void riseBelowLiveMinScoreMarksAnchor() {
        GameEvent anchor = event(91L, 4, "full_count_two_out");
        prepare(List.of(score(0, 26), score(5, 38)), List.of(anchor));

        int marked = backfill(ENABLED).backfillIfEmpty(GAME_ID, NOW, false);

        assertThat(marked).isEqualTo(1);
        assertThat(anchor.isTimelineHighlight()).isTrue();
        verify(gameEventRepository).save(anchor);
    }

    @Test
    @DisplayName("급변 폭이 부족하면 표시하지 않는다")
    void insufficientRiseKeepsEmpty() {
        prepare(
                List.of(score(0, 26), score(5, 37)),
                List.of(event(91L, 4, "full_count_two_out"))
        );

        int marked = backfill(ENABLED).backfillIfEmpty(GAME_ID, NOW, true);

        assertThat(marked).isZero();
        verify(gameEventRepository, never()).save(any());
        verify(aiGenerationTrigger, never()).onGameEventPersisted(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("급변 윈도 안에 보호 anchor가 없으면 스킵한다")
    void noAnchorInRiseWindowSkips() {
        prepare(
                List.of(score(0, 26), score(5, 38)),
                List.of(event(91L, -2, "full_count_two_out"))
        );

        int marked = backfill(ENABLED).backfillIfEmpty(GAME_ID, NOW, true);

        assertThat(marked).isZero();
        verify(gameEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("보호 라벨이 없는 이벤트는 anchor에서 제외한다")
    void unlabeledProtectedEventSkips() {
        prepare(
                List.of(score(0, 26), score(5, 38)),
                List.of(event(91L, 4, "unknown_event"))
        );

        int marked = backfill(ENABLED).backfillIfEmpty(GAME_ID, NOW, true);

        assertThat(marked).isZero();
        verify(gameEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("설정된 경기당 백필 상한까지만 표시한다")
    void limitsHighlightsPerGame() {
        ScoringProperties.Highlight config = new ScoringProperties.Highlight(true, 40, 12, 10, 8, 2);
        GameEvent first = event(91L, 4, "full_count_two_out");
        GameEvent second = event(92L, 24, "pressure_bases_loaded");
        GameEvent third = event(93L, 44, "hard_contact");
        prepare(
                List.of(
                        score(0, 10), score(5, 30), score(10, 35),
                        score(20, 10), score(25, 45), score(30, 50),
                        score(40, 10), score(45, 55)
                ),
                List.of(first, second, third)
        );

        int marked = backfill(config).backfillIfEmpty(GAME_ID, NOW, false);

        assertThat(marked).isEqualTo(2);
        assertThat(List.of(first, second, third).stream()
                .filter(GameEvent::isTimelineHighlight)
                .count()).isEqualTo(2);
        verify(gameEventRepository, times(2)).save(any(GameEvent.class));
    }

    @Test
    @DisplayName("기존 하이라이트를 해제한 뒤 다시 선정한다")
    void rebuildClearsExistingHighlightBeforeReselection() {
        GameEvent existingHighlight = event(91L, 4, "full_count_two_out");
        existingHighlight.setTimelineHighlight(true);
        List<Boolean> savedStates = new ArrayList<>();
        when(gameEventRepository
                .findByGameIdAndSpoilerLevelAndTimelineHighlightTrueOrderByObservedAtAscIdAsc(
                        GAME_ID, GameEvent.SPOILER_PROTECTED_SAFE))
                .thenReturn(List.of(existingHighlight));
        when(watchScoreRepository.findByGameIdOrderByComputedAtAsc(GAME_ID))
                .thenReturn(List.of(score(0, 26), score(5, 38)));
        when(gameEventRepository.findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                GAME_ID, GameEvent.SPOILER_PROTECTED_SAFE))
                .thenReturn(List.of(existingHighlight));
        when(gameEventRepository.save(any(GameEvent.class))).thenAnswer(invocation -> {
            GameEvent saved = invocation.getArgument(0);
            savedStates.add(saved.isTimelineHighlight());
            return saved;
        });

        int marked = backfill(ENABLED).rebuildHighlights(GAME_ID, NOW, false);

        assertThat(marked).isEqualTo(1);
        assertThat(savedStates).containsExactly(false, true);
        assertThat(existingHighlight.isTimelineHighlight()).isTrue();
    }

    @Test
    @DisplayName("재생성은 경기당 상한을 적용하지 않는다")
    void rebuildDoesNotLimitHighlightsPerGame() {
        ScoringProperties.Highlight config =
                new ScoringProperties.Highlight(true, 40, 12, 10, 8, 2);
        GameEvent first = event(91L, 4, "full_count_two_out");
        GameEvent second = event(92L, 24, "pressure_bases_loaded");
        GameEvent third = event(93L, 44, "hard_contact");
        when(gameEventRepository
                .findByGameIdAndSpoilerLevelAndTimelineHighlightTrueOrderByObservedAtAscIdAsc(
                        GAME_ID, GameEvent.SPOILER_PROTECTED_SAFE))
                .thenReturn(List.of());
        when(watchScoreRepository.findByGameIdOrderByComputedAtAsc(GAME_ID)).thenReturn(List.of(
                score(0, 10), score(5, 30), score(10, 35),
                score(20, 10), score(25, 45), score(30, 50),
                score(40, 10), score(45, 55)
        ));
        when(gameEventRepository.findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                GAME_ID, GameEvent.SPOILER_PROTECTED_SAFE))
                .thenReturn(List.of(first, second, third));

        int marked = backfill(config).rebuildHighlights(GAME_ID, NOW, false);

        assertThat(marked).isEqualTo(3);
        assertThat(List.of(first, second, third)).allMatch(GameEvent::isTimelineHighlight);
        verify(gameEventRepository, times(3)).save(any(GameEvent.class));
    }

    @Test
    @DisplayName("쿨다운 미만 간격의 급변 후보는 건너뛴다")
    void skipsRiseCandidateInsideCooldown() {
        ScoringProperties.Highlight config = new ScoringProperties.Highlight(true, 40, 12, 10, 8, 8);
        GameEvent anchor = event(91L, 4, "full_count_two_out");
        prepare(
                List.of(score(0, 10), score(5, 30), score(10, 35)),
                List.of(anchor)
        );

        int marked = backfill(config).backfillIfEmpty(GAME_ID, NOW, false);

        assertThat(marked).isEqualTo(1);
        verify(gameEventRepository).save(anchor);
    }

    @Test
    @DisplayName("하나의 anchor를 여러 급변 후보에 중복 선정하지 않는다")
    void doesNotSelectSameAnchorTwice() {
        ScoringProperties.Highlight config = new ScoringProperties.Highlight(true, 40, 12, 10, 0, 8);
        GameEvent anchor = event(91L, 4, "full_count_two_out");
        prepare(
                List.of(score(0, 10), score(5, 30), score(10, 35)),
                List.of(anchor)
        );

        int marked = backfill(config).backfillIfEmpty(GAME_ID, NOW, false);

        assertThat(marked).isEqualTo(1);
        verify(gameEventRepository).save(anchor);
    }

    @Test
    @DisplayName("문구 생성 요청이 꺼져 있으면 하이라이트만 표시한다")
    void copyGenerationDisabledDoesNotRequestCopy() {
        prepare(
                List.of(score(0, 26), score(5, 38)),
                List.of(event(91L, 4, "full_count_two_out"))
        );

        backfill(ENABLED).backfillIfEmpty(GAME_ID, NOW, false);

        verify(aiGenerationTrigger, never()).onGameEventPersisted(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("문구 생성 요청이 켜져 있으면 보호 문구 생성을 요청한다")
    void copyGenerationEnabledRequestsCopy() {
        prepare(
                List.of(score(0, 26), score(5, 38)),
                List.of(event(91L, 4, "full_count_two_out"))
        );

        backfill(ENABLED).backfillIfEmpty(GAME_ID, NOW, true);

        verify(aiGenerationTrigger).onGameEventPersisted(
                eq(GAME_ID), eq(91L), eq(AiGenerationTrigger.MODE_PROTECTED), eq(NOW));
    }

    @Test
    @DisplayName("하이라이트 설정이 비활성화면 아무 것도 하지 않는다")
    void disabledIsNoOp() {
        ScoringProperties.Highlight disabled = new ScoringProperties.Highlight(false, 40, 12, 6, 8, 8);

        int marked = backfill(disabled).backfillIfEmpty(GAME_ID, NOW, true);

        assertThat(marked).isZero();
        verify(gameEventRepository, never()).existsByGameIdAndTimelineHighlightTrue(anyLong());
        verify(gameEventRepository, never()).save(any());
    }

    private void prepare(List<WatchScore> scores, List<GameEvent> events) {
        when(gameEventRepository.existsByGameIdAndTimelineHighlightTrue(GAME_ID)).thenReturn(false);
        when(watchScoreRepository.findByGameIdOrderByComputedAtAsc(GAME_ID)).thenReturn(scores);
        when(gameEventRepository.findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                GAME_ID, GameEvent.SPOILER_PROTECTED_SAFE)).thenReturn(events);
    }

    private static WatchScore score(long minutesAfterStart, int value) {
        WatchScore score = new WatchScore();
        score.setGameId(GAME_ID);
        score.setComputedAt(START.plus(minutesAfterStart, ChronoUnit.MINUTES));
        score.setWatchScore(value);
        return score;
    }

    private static GameEvent event(long id, long minutesAfterStart, String eventType) {
        GameEvent event = new GameEvent();
        event.setId(id);
        event.setGameId(GAME_ID);
        event.setSpoilerLevel(GameEvent.SPOILER_PROTECTED_SAFE);
        event.setEventType(eventType);
        event.setObservedAt(START.plus(minutesAfterStart, ChronoUnit.MINUTES));
        return event;
    }
}
