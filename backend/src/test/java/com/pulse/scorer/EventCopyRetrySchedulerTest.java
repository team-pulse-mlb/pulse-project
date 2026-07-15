package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.ai.AiCopyMode;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.PageRequest;

class EventCopyRetrySchedulerTest {

    private static final Instant NOW = Instant.parse("2026-07-15T12:00:00Z");
    private static final EventCopyRetryProperties PROPERTIES = new EventCopyRetryProperties(
            Duration.ofSeconds(180), Duration.ofHours(6), 3, 50);

    private final GameEventRepository gameEventRepository = mock(GameEventRepository.class);
    private final AiEventCopyGenerator generator = mock(AiEventCopyGenerator.class);
    private final EventCopyRetryScheduler scheduler = new EventCopyRetryScheduler(
            gameEventRepository,
            generator,
            PROPERTIES,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void retryMissingCopies_shouldGenerateProtectedAndRevealedTargetsSynchronously() {
        GameEvent protectedTarget = event(10L, 100L);
        GameEvent revealedTarget = event(20L, 200L);
        Instant since = NOW.minus(Duration.ofHours(6));
        PageRequest batch = PageRequest.of(0, 50);

        when(gameEventRepository.findProtectedCopyRetryTargets(3, since, batch))
                .thenReturn(List.of(protectedTarget));
        when(gameEventRepository.findRevealedCopyRetryTargets(3, since, batch))
                .thenReturn(List.of(revealedTarget));
        when(generator.generateSynchronously(100L, 10L, AiCopyMode.PROTECTED))
                .thenReturn(AiEventCopyGenerator.GenerationStatus.SAVED);
        when(generator.generateSynchronously(200L, 20L, AiCopyMode.REVEALED))
                .thenReturn(AiEventCopyGenerator.GenerationStatus.CALL_FAILED);

        scheduler.retryMissingCopies();

        verify(gameEventRepository).findProtectedCopyRetryTargets(3, since, batch);
        verify(gameEventRepository).findRevealedCopyRetryTargets(3, since, batch);
        verify(generator).generateSynchronously(100L, 10L, AiCopyMode.PROTECTED);
        verify(generator).generateSynchronously(200L, 20L, AiCopyMode.REVEALED);
    }

    @Test
    void retryMissingCopies_shouldContinueAfterIndividualEventException() {
        GameEvent failedTarget = event(10L, 100L);
        GameEvent nextTarget = event(20L, 200L);
        Instant since = NOW.minus(Duration.ofHours(6));
        PageRequest batch = PageRequest.of(0, 50);

        when(gameEventRepository.findProtectedCopyRetryTargets(3, since, batch))
                .thenReturn(List.of(failedTarget, nextTarget));
        when(gameEventRepository.findRevealedCopyRetryTargets(3, since, batch))
                .thenReturn(List.of());
        when(generator.generateSynchronously(100L, 10L, AiCopyMode.PROTECTED))
                .thenThrow(new IllegalStateException("개별 이벤트 실패"));
        when(generator.generateSynchronously(200L, 20L, AiCopyMode.PROTECTED))
                .thenReturn(AiEventCopyGenerator.GenerationStatus.SAVED);

        scheduler.retryMissingCopies();

        verify(generator).generateSynchronously(100L, 10L, AiCopyMode.PROTECTED);
        verify(generator).generateSynchronously(200L, 20L, AiCopyMode.PROTECTED);
        verify(generator, never()).generateSynchronously(200L, 20L, AiCopyMode.REVEALED);
    }

    @Test
    void scorer가_활성화되면_재시도_스케줄러를_등록한다() {
        contextRunner().withPropertyValues("pulse.scorer.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(EventCopyRetryScheduler.class));
    }

    @Test
    void scorer가_비활성화되면_재시도_스케줄러를_등록하지_않는다() {
        contextRunner().withPropertyValues("pulse.scorer.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(EventCopyRetryScheduler.class));
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(EventCopyRetryScheduler.class)
                .withBean(GameEventRepository.class, () -> gameEventRepository)
                .withBean(AiEventCopyGenerator.class, () -> generator)
                .withBean(EventCopyRetryProperties.class, () -> PROPERTIES);
    }

    private static GameEvent event(long eventId, long gameId) {
        GameEvent event = new GameEvent();
        event.setId(eventId);
        event.setGameId(gameId);
        return event;
    }
}
