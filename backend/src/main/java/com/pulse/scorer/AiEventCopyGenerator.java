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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

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
        if (response.isEmpty()) {
            log.warn("AI 이벤트 문구 응답 없음: gameId={} eventId={} mode={}",
                    gameId, eventId, mode);
            return;
        }

        AiCopyResponse copyResponse = response.orElseThrow();
        if (!isStorable(copyResponse)) {
            logRejectedResponse(gameId, eventId, mode, copyResponse);
            return;
        }

        String latestContextHash = contextReader.eventCopyContext(gameId, eventId, mode)
                .map(EventCopyContext::contextHash)
                .orElse(null);
        if (!copyResponse.contextHash().equals(latestContextHash)) {
            log.info("AI 이벤트 문구 stale 응답 폐기: gameId={} eventId={} mode={} responseContextHash={} latestContextHash={}",
                    gameId, eventId, mode, copyResponse.contextHash(), latestContextHash);
            return;
        }

        GameEvent event = gameEventRepository.findById(eventId).orElse(null);
        if (event == null || event.getGameId() == null || event.getGameId() != gameId) {
            log.warn("AI 이벤트 문구 저장 대상 이벤트 없음/불일치: gameId={} eventId={} mode={}",
                    gameId, eventId, mode);
            return;
        }

        saveCopy(event, mode, copyResponse);
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
                && !isBlank(response.safeTitle());
    }

    private static void logRejectedResponse(
            long gameId,
            long eventId,
            AiCopyMode mode,
            AiCopyResponse response
    ) {
        log.warn(
                "AI 이벤트 문구 검수 반려/저장 조건 불충족: gameId={} eventId={} mode={} spoilerSafe={} fallbackUsed={} safeTitleBlank={} contextHashMissing={} violations={}",
                gameId,
                eventId,
                mode,
                response.spoilerSafe(),
                response.fallbackUsed(),
                isBlank(response.safeTitle()),
                response.contextHash() == null,
                response.violations()
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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

