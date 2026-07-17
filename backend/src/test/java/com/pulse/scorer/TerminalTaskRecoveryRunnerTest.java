package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.message.ScoreTask;
import com.pulse.common.message.ScoreTaskPublisher;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.poller.GameLifecycle;
import com.pulse.poller.ScoreTaskFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TerminalTaskRecoveryRunnerTest {

    private static final Duration RECOVERY_WINDOW = Duration.ofHours(24);

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final GameFinalizationService gameFinalizationService = mock(GameFinalizationService.class);
    private final ScoreTaskPublisher scoreTaskPublisher = mock(ScoreTaskPublisher.class);
    private final Instant now = Instant.parse("2026-07-08T04:00:00Z");
    private final TerminalTaskRecoveryRunner runner = new TerminalTaskRecoveryRunner(
            gameRepository,
            gameFinalizationService,
            new ScoreTaskFactory(),
            scoreTaskPublisher,
            RECOVERY_WINDOW,
            Clock.fixed(now, ZoneOffset.UTC)
    );

    @Test
    void recover_shouldRepublishTerminalTaskWhenFinalizationRecordIsMissing() {
        Game finalGame = game(100L, GameLifecycle.FINAL);
        when(gameRepository.findByLifecycleStateInAndUpdatedAtAfter(
                List.of(GameLifecycle.FINAL.name(), GameLifecycle.DONE.name()),
                now.minus(RECOVERY_WINDOW)
        )).thenReturn(List.of(finalGame));
        when(gameFinalizationService.hasFinalizationRecord(100L)).thenReturn(false);

        runner.recover();

        ArgumentCaptor<ScoreTask> taskCaptor = ArgumentCaptor.forClass(ScoreTask.class);
        verify(scoreTaskPublisher).publish(taskCaptor.capture());
        assertThat(taskCaptor.getValue().gameId()).isEqualTo(100L);
        assertThat(taskCaptor.getValue().lifecycleState()).isEqualTo(GameLifecycle.FINAL.name());
        assertThat(taskCaptor.getValue().observedAt()).isEqualTo(now);
    }

    @Test
    void recover_shouldSkipGameWithFinalizationRecord() {
        Game doneGame = game(200L, GameLifecycle.DONE);
        when(gameRepository.findByLifecycleStateInAndUpdatedAtAfter(
                List.of(GameLifecycle.FINAL.name(), GameLifecycle.DONE.name()),
                now.minus(RECOVERY_WINDOW)
        )).thenReturn(List.of(doneGame));
        when(gameFinalizationService.hasFinalizationRecord(200L)).thenReturn(true);

        runner.recover();

        verify(scoreTaskPublisher, never()).publish(org.mockito.ArgumentMatchers.any(ScoreTask.class));
    }

    private static Game game(long gameId, GameLifecycle lifecycle) {
        Game game = new Game();
        game.setId(gameId);
        game.setLifecycleState(lifecycle.name());
        return game;
    }
}
