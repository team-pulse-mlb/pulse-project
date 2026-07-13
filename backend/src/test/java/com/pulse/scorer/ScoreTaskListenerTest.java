package com.pulse.scorer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pulse.common.message.ScoreTask;
import com.pulse.poller.GameLifecycle;
import com.pulse.poller.ScoreTaskFactory;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ScoreTaskListenerTest {

    private final LiveScoringService liveScoringService = mock(LiveScoringService.class);
    private final PregameScoringService pregameScoringService = mock(PregameScoringService.class);
    private final GameFinalizationService gameFinalizationService = mock(GameFinalizationService.class);
    private final ScoreTaskListener listener = new ScoreTaskListener(
            liveScoringService,
            pregameScoringService,
            gameFinalizationService
    );

    @Test
    void handle_shouldRoutePregameTask() {
        ScoreTask task = task(ScoreTaskFactory.PREGAME_LIFECYCLE);

        listener.handle(task);

        verify(pregameScoringService).handle(task);
        verifyNoInteractions(liveScoringService, gameFinalizationService);
    }

    @Test
    void handle_shouldRouteTerminalTask() {
        ScoreTask task = task(GameLifecycle.FINAL.name());

        listener.handle(task);

        verify(gameFinalizationService).handle(task);
        verifyNoInteractions(liveScoringService, pregameScoringService);
    }

    @Test
    void handle_shouldRouteLiveTaskByDefault() {
        ScoreTask task = task(GameLifecycle.LIVE.name());

        listener.handle(task);

        verify(liveScoringService).handle(task);
        verifyNoInteractions(pregameScoringService, gameFinalizationService);
    }

    private static ScoreTask task(String lifecycleState) {
        return new ScoreTask(100L, Instant.parse("2026-07-08T01:00:00Z"), null, lifecycleState, null);
    }
}
