package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    void generate_shouldSaveProtectedCopyAndPublishGameSignal() {
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
        assertThat(event.getCopyProtectedContextHash()).isEqualTo("hash-1");
        verify(gameEventRepository).save(event);
        verify(liveSignalPublisher).publishGameSignal(10L);
    }

    @Test
    void generate_shouldDiscardResponseWhenLatestContextHashChanged() {
        ProtectedEventCopyContext requestedContext = context("hash-1");
        ProtectedEventCopyContext latestContext = context("hash-2");
        AiEventCopyRequest request = request("hash-1");
        AiCopyResponse response = new AiCopyResponse(true, "hash-1", "긴 승부가 이어졌습니다.", List.of(), false);

        when(contextReader.eventCopyContext(10L, 20L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(requestedContext))
                .thenReturn(Optional.of(latestContext));
        when(contextMapper.toRequest(AiCopyMode.PROTECTED, requestedContext)).thenReturn(request);
        when(aiServiceClient.generateEventCopy(request)).thenReturn(Optional.of(response));

        generator.generate(10L, 20L, AiGenerationTrigger.MODE_PROTECTED);

        verify(gameEventRepository, never()).save(any(GameEvent.class));
        verify(liveSignalPublisher, never()).publishGameSignal(10L);
    }

    @Test
    void generate_shouldRejectUnsafeResponse() {
        ProtectedEventCopyContext context = context("hash-1");
        AiEventCopyRequest request = request("hash-1");
        AiCopyResponse response = new AiCopyResponse(false, "hash-1", null, List.of("FORBIDDEN"), false);

        when(contextReader.eventCopyContext(10L, 20L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(context));
        when(contextMapper.toRequest(AiCopyMode.PROTECTED, context)).thenReturn(request);
        when(aiServiceClient.generateEventCopy(request)).thenReturn(Optional.of(response));

        generator.generate(10L, 20L, AiGenerationTrigger.MODE_PROTECTED);

        verify(gameEventRepository, never()).save(any(GameEvent.class));
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
