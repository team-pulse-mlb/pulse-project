package com.pulse.scorer;

import com.pulse.ai.AiCopyResponse;
import com.pulse.ai.AiEventCopyContextMapper;
import com.pulse.ai.AiEventCopyRequest;
import com.pulse.ai.AiServiceClient;
import com.pulse.common.ai.AiCopyContextReader;
import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.EventCopyContext;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 현재 구현된 AI 이벤트 문구 클라이언트를 scorer 생성 트리거에 연결한다. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
class AiEventCopyGenerator {

    private final AiCopyContextReader contextReader;
    private final AiEventCopyContextMapper contextMapper;
    private final AiServiceClient aiServiceClient;
    private final GameEventRepository gameEventRepository;
    private final LiveSignalPublisher liveSignalPublisher;

    @Async(AiGenerationAsyncConfig.TASK_EXECUTOR)
    public void generate(long gameId, long eventId, String modeValue) {
        AiCopyMode mode = parseMode(modeValue);
        if (mode == null) {
            log.warn("지원하지 않는 AI 이벤트 문구 모드: gameId={} eventId={} mode={}",
                    gameId, eventId, modeValue);
            return;
        }

        Optional<EventCopyContext> context = contextReader.eventCopyContext(gameId, eventId, mode);
        if (context.isEmpty()) {
            log.debug("AI 이벤트 문구 생성 대상 아님: gameId={} eventId={} mode={}",
                    gameId, eventId, mode);
            return;
        }

        AiEventCopyRequest request = contextMapper.toRequest(mode, context.get());
        Optional<AiCopyResponse> response = aiServiceClient.generateEventCopy(request);
        if (response.isEmpty() || !isStorable(response.get())) {
            log.debug("AI 이벤트 문구 저장 조건 불충족: gameId={} eventId={} mode={}",
                    gameId, eventId, mode);
            return;
        }

        String latestContextHash = contextReader.eventCopyContext(gameId, eventId, mode)
                .map(EventCopyContext::contextHash)
                .orElse(null);
        if (!response.get().contextHash().equals(latestContextHash)) {
            log.info("AI 이벤트 문구 stale 응답 폐기: gameId={} eventId={} mode={}",
                    gameId, eventId, mode);
            return;
        }

        GameEvent event = gameEventRepository.findById(eventId).orElse(null);
        if (event == null || event.getGameId() == null || event.getGameId() != gameId) {
            return;
        }

        saveCopy(event, mode, response.get());
        gameEventRepository.save(event);
        liveSignalPublisher.publishGameSignal(gameId);
    }

    private static AiCopyMode parseMode(String value) {
        try {
            return value == null ? null : AiCopyMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean isStorable(AiCopyResponse response) {
        return response.spoilerSafe()
                && !response.fallbackUsed()
                && response.contextHash() != null
                && response.safeTitle() != null
                && !response.safeTitle().isBlank();
    }

    private static void saveCopy(GameEvent event, AiCopyMode mode, AiCopyResponse response) {
        if (mode == AiCopyMode.PROTECTED) {
            event.setCopyProtected(response.safeTitle());
            event.setCopyProtectedContextHash(response.contextHash());
            return;
        }

        event.setCopyRevealed(response.safeTitle());
        event.setCopyRevealedContextHash(response.contextHash());
    }
}
