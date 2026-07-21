package com.pulse.scorer;

import com.pulse.scoring.ImportanceCalculator;
import com.pulse.scoring.ScoreCalculator;
import com.pulse.scoring.ScoringInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 리팩토링 안전망: LiveScoringService.handle()의 라이브 파이프라인 현재 동작을 고정한다.
 *
 * scorer를 이벤트 기반으로 쪼개기 전에, 계산·적재·후처리 팬아웃의 호출 순서와
 * 멱등(중복 사이클 skip, 재전달 시 1회 실행)을 특성화 테스트로 박아둔다.
 * 커밋/롤백 위상 게이팅은 {@link LiveScoringServiceCommitPhaseTest}가 담당한다.
 */
class LiveScoringServiceCharacterizationTest {

    private static final long GAME_ID = 5059180L;
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-17T01:00:00Z");

    @Test
    void handle_shouldRunEffectsInPipelineOrder() {
        Fixture fixture = new Fixture();

        fixture.service.handle(liveTask());

        // 계산 → watch_scores 적재 → peak 갱신 → game_events 추출 →
        // Redis 반영 → SURGE 판정 → 하이라이트 → 이벤트 발행 순서를 고정한다.
        // 미번역 플레이 요청은 커밋 후 PlayTranslationCommitListener가 이벤트로 처리한다.
        InOrder order = inOrder(
                fixture.calculator,
                fixture.watchScoreRepository,
                fixture.gameRepository,
                fixture.gameEventExtractor,
                fixture.liveSignalPublisher,
                fixture.surgeDetector,
                fixture.timelineHighlightTrigger,
                fixture.applicationEventPublisher);
        order.verify(fixture.calculator).calculate(any(ScoringInput.class));
        order.verify(fixture.watchScoreRepository).save(any(WatchScore.class));
        order.verify(fixture.gameRepository).save(fixture.game);
        order.verify(fixture.gameEventExtractor)
                .extract(eq(GAME_ID), anyList(), any(), anyInt(), eq(OBSERVED_AT));
        order.verify(fixture.liveSignalPublisher).publishLiveUpdate(
                eq(GAME_ID), anyDouble(), anyInt(), anyList(),
                any(), any(), any(), eq("LIVE"), anyList(), eq(OBSERVED_AT));
        order.verify(fixture.surgeDetector).evaluate(eq(GAME_ID), anyInt(), eq(OBSERVED_AT), any());
        order.verify(fixture.timelineHighlightTrigger).evaluate(eq(GAME_ID), anyInt(), eq(OBSERVED_AT));
        order.verify(fixture.applicationEventPublisher)
                .publishEvent(any(LiveScoreComputedEvent.class));
    }

    @Test
    void handle_shouldSkipEverythingWhenCycleAlreadyComputed() {
        Fixture fixture = new Fixture();
        when(fixture.watchScoreRepository.existsByGameIdAndComputedAt(GAME_ID, OBSERVED_AT))
                .thenReturn(true);

        fixture.service.handle(liveTask());

        // 이미 계산된 (gameId, computedAt)는 경기 조회 이전에 즉시 종료해 어떤 효과도 실행하지 않는다.
        verifyNoInteractions(
                fixture.gameRepository,
                fixture.playRepository,
                fixture.calculator,
                fixture.gameEventExtractor,
                fixture.liveSignalPublisher,
                fixture.surgeDetector,
                fixture.timelineHighlightTrigger,
                fixture.applicationEventPublisher);
        verify(fixture.watchScoreRepository, never()).save(any(WatchScore.class));
    }

    @Test
    void handle_shouldComputeExactlyOnceAcrossRedelivery() {
        Fixture fixture = new Fixture();
        // 같은 사이클이 재전달되면 두 번째 관측에서 UNIQUE 중복으로 skip 되어야 한다.
        when(fixture.watchScoreRepository.existsByGameIdAndComputedAt(GAME_ID, OBSERVED_AT))
                .thenReturn(false, true);

        fixture.service.handle(liveTask());
        fixture.service.handle(liveTask());

        verify(fixture.calculator, times(1)).calculate(any(ScoringInput.class));
        verify(fixture.watchScoreRepository, times(1)).save(any(WatchScore.class));
        verify(fixture.gameEventExtractor, times(1))
                .extract(eq(GAME_ID), anyList(), any(), anyInt(), eq(OBSERVED_AT));
        verify(fixture.liveSignalPublisher, times(1)).publishLiveUpdate(
                eq(GAME_ID), anyDouble(), anyInt(), anyList(),
                any(), any(), any(), eq("LIVE"), anyList(), eq(OBSERVED_AT));
        verify(fixture.surgeDetector, times(1)).evaluate(eq(GAME_ID), anyInt(), eq(OBSERVED_AT), any());
        verify(fixture.applicationEventPublisher, times(1))
                .publishEvent(any(LiveScoreComputedEvent.class));
    }

    @Test
    void handle_shouldPublishLiveScoreComputedEventWithComputedValues() {
        Fixture fixture = new Fixture();

        fixture.service.handle(liveTask());

        ArgumentCaptor<LiveScoreComputedEvent> eventCaptor =
                ArgumentCaptor.forClass(LiveScoreComputedEvent.class);
        verify(fixture.applicationEventPublisher).publishEvent(eventCaptor.capture());
        LiveScoreComputedEvent event = eventCaptor.getValue();
        assertThat(event.gameId()).isEqualTo(GAME_ID);
        assertThat(event.computedAt()).isEqualTo(OBSERVED_AT);
        assertThat(event.lifecycleState()).isEqualTo("LIVE");
        assertThat(event.scoringVersion()).isEqualTo(fixture.properties.version());
        assertThat(event.watchScoreRounded()).isEqualTo(80);
        assertThat(event.baseScoreRounded()).isEqualTo(60);
        assertThat(event.inning()).isEqualTo(8);
        assertThat(event.translationThroughPlayOrder()).isNull();
    }

    private static ScoreTask liveTask() {
        return new ScoreTask(GAME_ID, OBSERVED_AT, null, "LIVE", null);
    }

    private static Game liveGame() {
        Game game = new Game();
        game.setId(GAME_ID);
        game.setStatus(Game.STATUS_IN_PROGRESS);
        game.setLifecycleState("LIVE");
        game.setPeriod(8);
        game.setHomeRuns(3);
        game.setAwayRuns(2);
        return game;
    }

    private static final class Fixture {

        private final ScoringProperties properties = TestScoringProperties.version5();
        private final GameRepository gameRepository = mock(GameRepository.class);
        private final PlayRepository playRepository = mock(PlayRepository.class);
        private final WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);
        private final ScoreCalculator calculator = mock(ScoreCalculator.class);
        private final ImportanceCalculator importanceCalculator = mock(ImportanceCalculator.class);
        private final GameEventExtractor gameEventExtractor = mock(GameEventExtractor.class);
        private final LiveSignalPublisher liveSignalPublisher = mock(LiveSignalPublisher.class);
        private final SurgeDetector surgeDetector = mock(SurgeDetector.class);
        private final TimelineHighlightTrigger timelineHighlightTrigger = mock(TimelineHighlightTrigger.class);
        private final SurgeNotificationPublisher surgeNotificationPublisher =
                mock(SurgeNotificationPublisher.class);
        private final ApplicationEventPublisher applicationEventPublisher =
                mock(ApplicationEventPublisher.class);
        private final Game game = liveGame();
        private final LiveScoringService service;

        private Fixture() {
            when(watchScoreRepository.existsByGameIdAndComputedAt(GAME_ID, OBSERVED_AT)).thenReturn(false);
            when(watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(GAME_ID))
                    .thenReturn(Optional.empty());
            when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
            when(importanceCalculator.multiplier(game)).thenReturn(1.0);
            when(calculator.calculate(any(ScoringInput.class)))
                    // baseScore를 양수로 두어 peak 갱신(gameRepository.save)을 순서 검증에 포함한다.
                    .thenReturn(new ScoreCalculator.Result(Map.of(), 60.0, false, 1.0, 0, 80.0));

            service = new LiveScoringService(
                    gameRepository,
                    playRepository,
                    watchScoreRepository,
                    calculator,
                    importanceCalculator,
                    gameEventExtractor,
                    liveSignalPublisher,
                    surgeDetector,
                    timelineHighlightTrigger,
                    surgeNotificationPublisher,
                    properties,
                    applicationEventPublisher);
        }
    }
}
