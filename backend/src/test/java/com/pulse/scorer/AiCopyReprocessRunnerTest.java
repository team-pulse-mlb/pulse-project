package com.pulse.scorer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.domain.PageRequest;

class AiCopyReprocessRunnerTest {

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final GameEventRepository gameEventRepository = mock(GameEventRepository.class);
    private final PlayRepository playRepository = mock(PlayRepository.class);
    private final AiFinalHeadlineGenerator finalHeadlineGenerator = mock(AiFinalHeadlineGenerator.class);
    private final AiEventCopyGenerator eventCopyGenerator = mock(AiEventCopyGenerator.class);
    private final AiPlayTranslationGenerator playTranslationGenerator =
            mock(AiPlayTranslationGenerator.class);
    private final TimelineHighlightBackfill timelineHighlightBackfill =
            mock(TimelineHighlightBackfill.class);
    private final ConfigurableApplicationContext applicationContext =
            mock(ConfigurableApplicationContext.class);

    private AiCopyReprocessRunner runner(AiCopyReprocessProperties properties) {
        return new AiCopyReprocessRunner(
                properties,
                gameRepository,
                gameEventRepository,
                playRepository,
                finalHeadlineGenerator,
                eventCopyGenerator,
                playTranslationGenerator,
                timelineHighlightBackfill,
                applicationContext
        );
    }

    @Test
    void 전체_종료_헤드라인과_보호_이벤트_문구와_플레이_번역을_강제_재생성한다() throws Exception {
        GameEvent event = new GameEvent();
        event.setId(20L);
        event.setGameId(200L);
        Play play = new Play();
        play.setId(30L);
        when(gameRepository.findAllFinalGameIds()).thenReturn(List.of(100L));
        when(finalHeadlineGenerator.regenerateSynchronously(100L))
                .thenReturn(AiFinalHeadlineGenerator.GenerationStatus.SAVED);
        when(gameEventRepository.countBySpoilerLevelAndTimelineHighlightTrue(
                GameEvent.SPOILER_PROTECTED_SAFE))
                .thenReturn(1L);
        when(gameEventRepository.findMaxProtectedEventId()).thenReturn(20L);
        when(gameEventRepository.findProtectedAiReprocessTargets(
                0L, 20L, PageRequest.of(0, 50)))
                .thenReturn(List.of(event));
        when(eventCopyGenerator.regenerateSynchronously(200L, 20L))
                .thenReturn(AiEventCopyGenerator.GenerationStatus.SAVED);
        when(playRepository.findPlayTranslationReprocessTargets(100L))
                .thenReturn(List.of(play));
        when(playTranslationGenerator.regenerateSynchronously(100L, 30L))
                .thenReturn(AiPlayTranslationGenerator.GenerationStatus.SAVED);

        runner(new AiCopyReprocessProperties(50, null, null, true, true, true, true))
                .run(new DefaultApplicationArguments());

        InOrder processingOrder = inOrder(timelineHighlightBackfill, finalHeadlineGenerator);
        processingOrder.verify(timelineHighlightBackfill)
                .rebuildHighlights(eq(100L), any(Instant.class), eq(false));
        processingOrder.verify(finalHeadlineGenerator).regenerateSynchronously(100L);
        verify(finalHeadlineGenerator).regenerateSynchronously(100L);
        verify(eventCopyGenerator).regenerateSynchronously(200L, 20L);
        verify(playTranslationGenerator).regenerateSynchronously(100L, 30L);
        verify(applicationContext).close();
    }

    @Test
    void 기간이_지정되면_해당_기간_종료_경기만_재생성한다() throws Exception {
        GameEvent event = new GameEvent();
        event.setId(20L);
        event.setGameId(100L);
        Play play = new Play();
        play.setId(30L);
        Instant startInclusive = Instant.parse("2026-07-12T15:00:00Z");
        Instant endExclusive = Instant.parse("2026-07-16T15:00:00Z");
        when(gameRepository.findFinalGameIdsByStartTimeBetween(startInclusive, endExclusive))
                .thenReturn(List.of(100L));
        when(finalHeadlineGenerator.regenerateSynchronously(100L))
                .thenReturn(AiFinalHeadlineGenerator.GenerationStatus.SAVED);
        when(gameEventRepository
                .findBySpoilerLevelAndTimelineHighlightTrueAndGameIdInOrderByGameIdAscObservedAtAsc(
                        GameEvent.SPOILER_PROTECTED_SAFE, List.of(100L)))
                .thenReturn(List.of(event));
        when(eventCopyGenerator.regenerateSynchronously(100L, 20L))
                .thenReturn(AiEventCopyGenerator.GenerationStatus.SAVED);
        when(playRepository.findPlayTranslationReprocessTargets(100L))
                .thenReturn(List.of(play));
        when(playTranslationGenerator.regenerateSynchronously(100L, 30L))
                .thenReturn(AiPlayTranslationGenerator.GenerationStatus.SAVED);

        runner(new AiCopyReprocessProperties(
                50, "2026-07-13", "2026-07-16", true, true, true, true))
                .run(new DefaultApplicationArguments());

        verify(gameRepository, never()).findAllFinalGameIds();
        verify(gameEventRepository, never())
                .findProtectedAiReprocessTargets(anyLong(), anyLong(), any());
        verify(gameEventRepository, never())
                .findBySpoilerLevelAndGameIdInOrderByGameIdAscObservedAtAsc(anyString(), any());
        verify(timelineHighlightBackfill).rebuildHighlights(eq(100L), any(Instant.class), eq(false));
        verify(finalHeadlineGenerator).regenerateSynchronously(100L);
        verify(eventCopyGenerator).regenerateSynchronously(100L, 20L);
        verify(playTranslationGenerator).regenerateSynchronously(100L, 30L);
        verify(applicationContext).close();
    }

    @Test
    void 헤드라인_단계만_켜면_다른_단계는_실행하지_않는다() throws Exception {
        when(gameRepository.findAllFinalGameIds()).thenReturn(List.of(100L));
        when(finalHeadlineGenerator.regenerateSynchronously(100L))
                .thenReturn(AiFinalHeadlineGenerator.GenerationStatus.SAVED);

        runner(new AiCopyReprocessProperties(50, null, null, false, true, false, false))
                .run(new DefaultApplicationArguments());

        verify(finalHeadlineGenerator).regenerateSynchronously(100L);
        verify(timelineHighlightBackfill, never())
                .rebuildHighlights(anyLong(), any(Instant.class), anyBoolean());
        verify(eventCopyGenerator, never()).regenerateSynchronously(anyLong(), anyLong());
        verify(playTranslationGenerator, never()).regenerateSynchronously(anyLong(), anyLong());
        verify(applicationContext).close();
    }
}
