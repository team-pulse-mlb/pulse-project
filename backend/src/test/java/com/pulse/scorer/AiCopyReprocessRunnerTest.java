package com.pulse.scorer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.GameRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.domain.PageRequest;

class AiCopyReprocessRunnerTest {

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final GameEventRepository gameEventRepository = mock(GameEventRepository.class);
    private final AiFinalHeadlineGenerator finalHeadlineGenerator = mock(AiFinalHeadlineGenerator.class);
    private final AiEventCopyGenerator eventCopyGenerator = mock(AiEventCopyGenerator.class);
    private final ConfigurableApplicationContext applicationContext =
            mock(ConfigurableApplicationContext.class);
    private final AiCopyReprocessRunner runner = new AiCopyReprocessRunner(
            new AiCopyReprocessProperties(50),
            gameRepository,
            gameEventRepository,
            finalHeadlineGenerator,
            eventCopyGenerator,
            applicationContext
    );

    @Test
    void 전체_종료_헤드라인과_보호_이벤트_문구를_강제_재생성한다() throws Exception {
        GameEvent event = new GameEvent();
        event.setId(20L);
        event.setGameId(200L);
        when(gameRepository.findAllFinalGameIds()).thenReturn(List.of(100L));
        when(finalHeadlineGenerator.regenerateSynchronously(100L))
                .thenReturn(AiFinalHeadlineGenerator.GenerationStatus.SAVED);
        when(gameEventRepository.countBySpoilerLevel(GameEvent.SPOILER_PROTECTED_SAFE))
                .thenReturn(1L);
        when(gameEventRepository.findMaxProtectedEventId()).thenReturn(20L);
        when(gameEventRepository.findProtectedAiReprocessTargets(
                0L, 20L, PageRequest.of(0, 50)))
                .thenReturn(List.of(event));
        when(eventCopyGenerator.regenerateSynchronously(200L, 20L))
                .thenReturn(AiEventCopyGenerator.GenerationStatus.SAVED);

        runner.run(new DefaultApplicationArguments());

        verify(finalHeadlineGenerator).regenerateSynchronously(100L);
        verify(eventCopyGenerator).regenerateSynchronously(200L, 20L);
        verify(applicationContext).close();
    }
}
