package com.pulse.gameprocessing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.gameprocessing.effect.LiveSignalPublisher;
import com.pulse.gameprocessing.event.LiveScoreComputedEvent;
import com.pulse.gameprocessing.highlight.GameEventExtractor;
import com.pulse.gameprocessing.highlight.TimelineHighlightTrigger;
import com.pulse.scoring.ImportanceCalculator;
import com.pulse.scoring.ScoreCalculator;
import com.pulse.scoring.ScoringInput;
import com.pulse.scoring.TestScoringProperties;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 재전달 순서 역전 경계 특성화: 더 새로운 사이클이 먼저 처리된 뒤
 * 더 오래된 observedAt의 ScoreTask가 재전달되는 경우를 고정한다.
 *
 * 멱등 체크는 (gameId, computedAt) 정확 일치뿐이라 순서 가드가 없다.
 * 옛 사이클도 그대로 계산·적재되고 옛 computedAt으로 이벤트가 발행되어
 * 커밋 후 Redis 반영이 stale 값으로 덮일 수 있는 경계다. peak만 단조 유지된다.
 */
class LiveScoringServiceRedeliveryOrderTest {

    private static final long GAME_ID = 5059180L;
    private static final Instant OLDER_OBSERVED_AT = Instant.parse("2026-07-17T01:00:00Z");
    private static final Instant NEWER_OBSERVED_AT = Instant.parse("2026-07-17T01:00:20Z");

    @Test
    void handle_shouldProcessOlderCycleRedeliveredAfterNewerCycle() {
        Fixture fixture = new Fixture();

        fixture.service.handle(task(NEWER_OBSERVED_AT));
        fixture.service.handle(task(OLDER_OBSERVED_AT));

        // 순서 가드가 없어 옛 사이클도 계산·적재되고 이벤트가 발행된다.
        verify(fixture.calculator, times(2)).calculate(any(ScoringInput.class));
        verify(fixture.watchScoreRepository, times(2)).save(any(WatchScore.class));

        ArgumentCaptor<LiveScoreComputedEvent> eventCaptor =
                ArgumentCaptor.forClass(LiveScoreComputedEvent.class);
        verify(fixture.applicationEventPublisher, times(2)).publishEvent(eventCaptor.capture());
        // 두 번째 발행 이벤트는 옛 computedAt을 그대로 실어 커밋 후 Redis 반영을 stale로 덮을 수 있다.
        assertThat(eventCaptor.getAllValues().get(0).computedAt()).isEqualTo(NEWER_OBSERVED_AT);
        assertThat(eventCaptor.getAllValues().get(1).computedAt()).isEqualTo(OLDER_OBSERVED_AT);
    }

    @Test
    void handle_shouldNotRegressPeakBaseScoreOnOlderCycleRedelivery() {
        Fixture fixture = new Fixture();
        // 이미 더 높은 peak가 기록된 상태에서 옛 사이클(baseScore 60)이 재전달돼도
        // peak는 단조 유지되어 gameRepository.save가 호출되지 않는다.
        fixture.game.setPeakBaseScore(90);

        fixture.service.handle(task(OLDER_OBSERVED_AT));

        verify(fixture.gameRepository, never()).save(any(Game.class));
        assertThat(fixture.game.getPeakBaseScore()).isEqualTo(90);
    }

    private static ScoreTask task(Instant observedAt) {
        return new ScoreTask(GAME_ID, observedAt, null, "LIVE", null);
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
        private final TimelineHighlightTrigger timelineHighlightTrigger = mock(TimelineHighlightTrigger.class);
        private final ApplicationEventPublisher applicationEventPublisher =
                mock(ApplicationEventPublisher.class);
        private final Game game = liveGame();
        private final LiveScoringService service;

        private Fixture() {
            // 사이클별 정확 일치 멱등 체크만 있어 서로 다른 computedAt은 모두 미계산으로 판단된다.
            when(watchScoreRepository.existsByGameIdAndComputedAt(any(Long.class), any(Instant.class)))
                    .thenReturn(false);
            when(watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(GAME_ID))
                    .thenReturn(Optional.empty());
            when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
            when(importanceCalculator.multiplier(game)).thenReturn(1.0);
            when(calculator.calculate(any(ScoringInput.class)))
                    .thenReturn(new ScoreCalculator.Result(Map.of(), 60.0, false, 1.0, 0, 80.0));

            service = new LiveScoringService(
                    gameRepository,
                    playRepository,
                    watchScoreRepository,
                    calculator,
                    importanceCalculator,
                    gameEventExtractor,
                    liveSignalPublisher,
                    timelineHighlightTrigger,
                    properties,
                    applicationEventPublisher);
        }
    }
}
