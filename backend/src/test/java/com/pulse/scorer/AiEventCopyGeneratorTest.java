package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.ai.AiCopyResponse;
import com.pulse.ai.AiEventCopyContextMapper;
import com.pulse.ai.AiEventCopyRequest;
import com.pulse.ai.AiServiceClient;
import com.pulse.common.ai.AiCopyContextReader;
import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.ProtectedEventCopyContext;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiEventCopyGeneratorTest {

    private final AiCopyContextReader contextReader = mock(AiCopyContextReader.class);
    private final AiEventCopyContextMapper contextMapper = mock(AiEventCopyContextMapper.class);
    private final AiServiceClient aiServiceClient = mock(AiServiceClient.class);
    private final GameEventRepository gameEventRepository = mock(GameEventRepository.class);
    private final LiveSignalPublisher liveSignalPublisher = mock(LiveSignalPublisher.class);
    private final AiEventCopyGenerator generator = new AiEventCopyGenerator(
            contextReader,
            contextMapper,
            aiServiceClient,
            gameEventRepository,
            liveSignalPublisher
    );

    @Test
    void generateSynchronously_shouldIncreaseAttemptsSaveProtectedCopyAndPublishGameSignal() {
        ProtectedEventCopyContext context = context("hash-1");
        AiEventCopyRequest request = request("hash-1");
        AiCopyResponse response = new AiCopyResponse(true, "hash-1", "긴 승부가 이어졌습니다.", List.of(), false);
        GameEvent event = event();

        when(contextReader.eventCopyContext(10L, 20L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(context));
        when(contextMapper.toRequest(AiCopyMode.PROTECTED, context)).thenReturn(request);
        when(aiServiceClient.generateEventCopy(request)).thenReturn(Optional.of(response));
        when(gameEventRepository.findById(20L)).thenReturn(Optional.of(event));

        AiEventCopyGenerator.GenerationStatus status =
                generator.generateSynchronously(10L, 20L, AiCopyMode.PROTECTED);

        assertThat(status).isEqualTo(AiEventCopyGenerator.GenerationStatus.SAVED);
        assertThat(event.getCopyProtectedAttempts()).isEqualTo(1);
        assertThat(event.getCopyProtected()).isEqualTo("긴 승부가 이어졌습니다.");
        assertThat(event.getCopyProtectedContextHash()).isEqualTo("hash-1");
        verify(gameEventRepository).save(event);
        verify(liveSignalPublisher).publishGameSignal(10L);
    }

    @Test
    void generateSynchronously_shouldRejectRevealedModeWithoutCallingAiService() {
        AiEventCopyGenerator.GenerationStatus status =
                generator.generateSynchronously(10L, 20L, AiCopyMode.REVEALED);

        assertThat(status).isEqualTo(AiEventCopyGenerator.GenerationStatus.NOT_ELIGIBLE);
        verify(contextReader, never()).eventCopyContext(10L, 20L, AiCopyMode.REVEALED);
        verify(aiServiceClient, never()).generateEventCopy(org.mockito.ArgumentMatchers.any());
        verify(gameEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateSynchronously_shouldIncreaseAttemptsAndReturnCallFailedWhenResponseIsEmpty() {
        ProtectedEventCopyContext context = context("hash-1");
        AiEventCopyRequest request = request("hash-1");
        GameEvent event = event();

        when(contextReader.eventCopyContext(10L, 20L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(context));
        when(contextMapper.toRequest(AiCopyMode.PROTECTED, context)).thenReturn(request);
        when(aiServiceClient.generateEventCopy(request)).thenReturn(Optional.empty());
        when(gameEventRepository.findById(20L)).thenReturn(Optional.of(event));

        AiEventCopyGenerator.GenerationStatus status =
                generator.generateSynchronously(10L, 20L, AiCopyMode.PROTECTED);

        assertThat(status).isEqualTo(AiEventCopyGenerator.GenerationStatus.CALL_FAILED);
        assertThat(event.getCopyProtectedAttempts()).isEqualTo(1);
        assertThat(event.getCopyProtected()).isNull();
        verify(gameEventRepository).save(event);
        verify(liveSignalPublisher, never()).publishGameSignal(10L);
    }

    @Test
    void generateSynchronously_shouldIncreaseAttemptsAndReturnReviewRejectedWhenResponseIsUnsafe() {
        ProtectedEventCopyContext context = context("hash-1");
        AiEventCopyRequest request = request("hash-1");
        AiCopyResponse response = new AiCopyResponse(
                false, "hash-1", null, List.of("FORBIDDEN_WORD:홈런"), false);
        GameEvent event = event();

        when(contextReader.eventCopyContext(10L, 20L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(context));
        when(contextMapper.toRequest(AiCopyMode.PROTECTED, context)).thenReturn(request);
        when(aiServiceClient.generateEventCopy(request)).thenReturn(Optional.of(response));
        when(gameEventRepository.findById(20L)).thenReturn(Optional.of(event));

        AiEventCopyGenerator.GenerationStatus status =
                generator.generateSynchronously(10L, 20L, AiCopyMode.PROTECTED);

        assertThat(status).isEqualTo(AiEventCopyGenerator.GenerationStatus.REVIEW_REJECTED);
        assertThat(event.getCopyProtectedAttempts()).isEqualTo(1);
        assertThat(event.getCopyProtected()).isNull();
        verify(gameEventRepository).save(event);
        verify(liveSignalPublisher, never()).publishGameSignal(10L);
    }

    @Test
    void generateSynchronously_shouldClassifyOpenAiTimeoutAsCallFailed() {
        ProtectedEventCopyContext context = context("hash-1");
        AiEventCopyRequest request = request("hash-1");
        AiCopyResponse response = new AiCopyResponse(
                false, "hash-1", null, List.of("OPENAI_TIMEOUT"), false);
        GameEvent event = event();

        when(contextReader.eventCopyContext(10L, 20L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(context));
        when(contextMapper.toRequest(AiCopyMode.PROTECTED, context)).thenReturn(request);
        when(aiServiceClient.generateEventCopy(request)).thenReturn(Optional.of(response));
        when(gameEventRepository.findById(20L)).thenReturn(Optional.of(event));

        AiEventCopyGenerator.GenerationStatus status =
                generator.generateSynchronously(10L, 20L, AiCopyMode.PROTECTED);

        assertThat(status).isEqualTo(AiEventCopyGenerator.GenerationStatus.CALL_FAILED);
        assertThat(event.getCopyProtectedAttempts()).isEqualTo(1);
        assertThat(event.getCopyProtected()).isNull();
        verify(gameEventRepository).save(event);
        verify(liveSignalPublisher, never()).publishGameSignal(10L);
    }

    @Test
    void generateSynchronously_shouldClassifyOpenAiGenerationErrorAsCallFailed() {
        ProtectedEventCopyContext context = context("hash-1");
        AiEventCopyRequest request = request("hash-1");
        AiCopyResponse response = new AiCopyResponse(
                false, "hash-1", null, List.of("OPENAI_GENERATION_FAILED"), false);
        GameEvent event = event();

        when(contextReader.eventCopyContext(10L, 20L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(context));
        when(contextMapper.toRequest(AiCopyMode.PROTECTED, context)).thenReturn(request);
        when(aiServiceClient.generateEventCopy(request)).thenReturn(Optional.of(response));
        when(gameEventRepository.findById(20L)).thenReturn(Optional.of(event));

        AiEventCopyGenerator.GenerationStatus status =
                generator.generateSynchronously(10L, 20L, AiCopyMode.PROTECTED);

        assertThat(status).isEqualTo(AiEventCopyGenerator.GenerationStatus.CALL_FAILED);
        assertThat(event.getCopyProtectedAttempts()).isEqualTo(1);
        assertThat(event.getCopyProtected()).isNull();
        verify(gameEventRepository).save(event);
        verify(liveSignalPublisher, never()).publishGameSignal(10L);
    }

    @Test
    void generateSynchronously_shouldIncreaseAttemptsAndDiscardResponseWhenLatestContextHashChanged() {
        ProtectedEventCopyContext requestedContext = context("hash-1");
        ProtectedEventCopyContext latestContext = context("hash-2");
        AiEventCopyRequest request = request("hash-1");
        AiCopyResponse response = new AiCopyResponse(true, "hash-1", "긴 승부가 이어졌습니다.", List.of(), false);
        GameEvent event = event();

        when(contextReader.eventCopyContext(10L, 20L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(requestedContext))
                .thenReturn(Optional.of(latestContext));
        when(contextMapper.toRequest(AiCopyMode.PROTECTED, requestedContext)).thenReturn(request);
        when(aiServiceClient.generateEventCopy(request)).thenReturn(Optional.of(response));
        when(gameEventRepository.findById(20L)).thenReturn(Optional.of(event));

        AiEventCopyGenerator.GenerationStatus status =
                generator.generateSynchronously(10L, 20L, AiCopyMode.PROTECTED);

        assertThat(status).isEqualTo(AiEventCopyGenerator.GenerationStatus.STALE);
        assertThat(event.getCopyProtectedAttempts()).isEqualTo(1);
        assertThat(event.getCopyProtected()).isNull();
        verify(gameEventRepository).save(event);
        verify(liveSignalPublisher, never()).publishGameSignal(10L);
    }

    @Test
    void generate_shouldKeepLiveTriggerPath() {
        ProtectedEventCopyContext context = context("hash-1");
        AiEventCopyRequest request = request("hash-1");
        AiCopyResponse response = new AiCopyResponse(true, "hash-1", "긴 승부가 이어졌습니다.", List.of(), false);
        GameEvent event = event();

        when(contextReader.eventCopyContext(10L, 20L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(context));
        when(contextMapper.toRequest(AiCopyMode.PROTECTED, context)).thenReturn(request);
        when(aiServiceClient.generateEventCopy(request)).thenReturn(Optional.of(response));
        when(gameEventRepository.findById(20L)).thenReturn(Optional.of(event));

        generator.generate(10L, 20L, AiGenerationTrigger.MODE_PROTECTED);

        assertThat(event.getCopyProtected()).isEqualTo("긴 승부가 이어졌습니다.");
        verify(liveSignalPublisher).publishGameSignal(10L);
    }

    private static ProtectedEventCopyContext context(String hash) {
        return new ProtectedEventCopyContext(10L, 20L, "long_at_bat", "긴 승부", 7, hash);
    }

    private static AiEventCopyRequest request(String hash) {
        return new AiEventCopyRequest(
                10L,
                20L,
                "PROTECTED",
                hash,
                new AiEventCopyRequest.SafeContext("long_at_bat", "긴 승부", 7, null, null, null, null)
        );
    }

    private static GameEvent event() {
        GameEvent event = new GameEvent();
        event.setId(20L);
        event.setGameId(10L);
        return event;
    }
}
