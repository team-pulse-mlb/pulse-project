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

        generateSynchronously(gameId, eventId, mode);
    }

    GenerationStatus generateSynchronously(long gameId, long eventId, AiCopyMode mode) {
        Optional<EventCopyContext> context = contextReader.eventCopyContext(gameId, eventId, mode);
        if (context.isEmpty()) {
            log.debug("AI 이벤트 문구 생성 대상 아님: gameId={} eventId={} mode={}",
                    gameId, eventId, mode);
            return GenerationStatus.NOT_ELIGIBLE;
        }

        GameEvent event = gameEventRepository.findById(eventId).orElse(null);
        if (event == null || event.getGameId() == null || event.getGameId() != gameId) {
            log.warn("AI 이벤트 문구 저장 대상 이벤트 없음/불일치: gameId={} eventId={} mode={}",
                    gameId, eventId, mode);
            return GenerationStatus.NOT_ELIGIBLE;
        }
        if (hasCopy(event, mode)) {
            log.debug("AI 이벤트 문구 이미 저장됨: gameId={} eventId={} mode={}",
                    gameId, eventId, mode);
            return GenerationStatus.ALREADY_PRESENT;
        }

        AiEventCopyRequest request = contextMapper.toRequest(mode, context.get());
        Optional<AiCopyResponse> response = aiServiceClient.generateEventCopy(request);
        incrementAttempts(event, mode);

        GenerationStatus status;
        if (response.isEmpty()) {
            log.warn("AI 이벤트 문구 호출 실패/타임아웃: gameId={} eventId={} mode={}",
                    gameId, eventId, mode);
            status = GenerationStatus.CALL_FAILED;
        } else {
            AiCopyResponse result = response.orElseThrow();
            status = processResponse(gameId, eventId, mode, event, result);
        }

        gameEventRepository.save(event);
        if (status == GenerationStatus.SAVED) {
            liveSignalPublisher.publishGameSignal(gameId);
        }
        return status;
    }

    private GenerationStatus processResponse(
            long gameId,
            long eventId,
            AiCopyMode mode,
            GameEvent event,
            AiCopyResponse response
    ) {
        if (hasViolation(response, "OPENAI_TIMEOUT")) {
            log.warn("AI 이벤트 문구 호출 타임아웃: gameId={} eventId={} mode={} violations={}",
                    gameId, eventId, mode, response.violations());
            return GenerationStatus.CALL_FAILED;
        }
        if (hasOpenAiFailure(response)) {
            log.warn("AI 이벤트 문구 호출 실패: gameId={} eventId={} mode={} violations={}",
                    gameId, eventId, mode, response.violations());
            return GenerationStatus.CALL_FAILED;
        }
        if (!isStorable(response)) {
            logRejectedResponse(gameId, eventId, mode, response);
            return GenerationStatus.REVIEW_REJECTED;
        }

        String latestContextHash = contextReader.eventCopyContext(gameId, eventId, mode)
                .map(EventCopyContext::contextHash)
                .orElse(null);
        if (!response.contextHash().equals(latestContextHash)) {
            log.info("AI 이벤트 문구 stale 응답 폐기: gameId={} eventId={} mode={} "
                            + "responseContextHash={} latestContextHash={}",
                    gameId, eventId, mode, response.contextHash(), latestContextHash);
            return GenerationStatus.STALE;
        }

        saveCopy(event, mode, response);
        return GenerationStatus.SAVED;
    }

    private static boolean hasOpenAiFailure(AiCopyResponse response) {
        return response.violations() != null
                && response.violations().stream()
                        .filter(violation -> violation != null)
                        .anyMatch(violation -> violation.startsWith("OPENAI_"));
    }

    private static boolean hasViolation(AiCopyResponse response, String expected) {
        return response.violations() != null && response.violations().contains(expected);
    }

    private static void logRejectedResponse(
            long gameId,
            long eventId,
            AiCopyMode mode,
            AiCopyResponse response
    ) {
        log.warn("AI 이벤트 문구 검수 반려: gameId={} eventId={} mode={} "
                        + "spoilerSafe={} fallbackUsed={} safeTitleBlank={} "
                        + "contextHashMissing={} violations={}",
                gameId,
                eventId,
                mode,
                response.spoilerSafe(),
                response.fallbackUsed(),
                isBlank(response.safeTitle()),
                response.contextHash() == null,
                response.violations());
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasCopy(GameEvent event, AiCopyMode mode) {
        return mode == AiCopyMode.PROTECTED
                ? event.getCopyProtected() != null
                : event.getCopyRevealed() != null;
    }

    private static void incrementAttempts(GameEvent event, AiCopyMode mode) {
        if (mode == AiCopyMode.PROTECTED) {
            event.setCopyProtectedAttempts(attempts(event.getCopyProtectedAttempts()) + 1);
            return;
        }
        event.setCopyRevealedAttempts(attempts(event.getCopyRevealedAttempts()) + 1);
    }

    private static int attempts(Integer value) {
        return value == null ? 0 : value;
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

    enum GenerationStatus {
        SAVED,
        ALREADY_PRESENT,
        NOT_ELIGIBLE,
        CALL_FAILED,
        REVIEW_REJECTED,
        STALE
    }
}
