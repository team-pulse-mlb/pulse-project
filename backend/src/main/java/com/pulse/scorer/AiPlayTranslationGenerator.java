package com.pulse.scorer;

import com.pulse.ai.AiPlayTranslationRequest;
import com.pulse.ai.AiPlayTranslationResponse;
import com.pulse.ai.AiServiceClient;
import com.pulse.common.ai.AiContextHashCalculator;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 공개 모드 최근 플레이용 한국어 번역을 생성하고 plays에 저장한다.
 *
 * 한 poll에서 여러 play가 저장돼도 ScoreTask는 마지막 play 순서만 전달하므로,
 * 해당 순서 이하의 미번역 타석 결과를 최신순으로 묶어서 처리한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "pulse.scorer",
        name = "enabled",
        havingValue = "true")
@RequiredArgsConstructor
class AiPlayTranslationGenerator {

    private static final String MODE_REVEALED = "REVEALED";
    private static final String TARGET_LANGUAGE = "ko";
    private static final String PLAY_RESULT_TYPE = "Play Result";

    private final AiServiceClient aiServiceClient;
    private final PlayRepository playRepository;
    private final LiveSignalPublisher liveSignalPublisher;
    private final PlayTranslationProperties properties;

    /**
     * 연속 ScoreTask가 들어와도 같은 play를 동시에 번역하지 않도록
     * 현재 JVM에서 처리 중인 내부 play ID를 기록한다.
     */
    private final Set<Long> inFlightPlayIds =
            ConcurrentHashMap.newKeySet();

    /**
     * scorer 메시지 소비 스레드를 막지 않고 미번역 타석 결과를 처리한다.
     */
    @Async(AiGenerationAsyncConfig.TASK_EXECUTOR)
    public void generatePending(
            long gameId,
            Long lastPlayOrder) {

        if (gameId <= 0 || lastPlayOrder == null) {
            return;
        }

        List<Play> pending =
                playRepository.findPendingPlayTranslations(
                        gameId,
                        lastPlayOrder,
                        properties.maxAttempts(),
                        PageRequest.of(
                                0,
                                properties.batchSize()));

        for (Play play : pending) {
            Long playId = play.getId();

            if (playId == null
                    || playId <= 0
                    || !inFlightPlayIds.add(playId)) {
                continue;
            }

            try {
                generateSynchronously(
                        gameId,
                        playId);
            } finally {
                inFlightPlayIds.remove(playId);
            }
        }
    }

    GenerationStatus generateSynchronously(
            long gameId,
            long playId) {

        Play play =
                playRepository.findById(playId)
                        .orElse(null);

        if (!isEligible(play, gameId)) {
            return GenerationStatus.NOT_ELIGIBLE;
        }

        if (hasText(play.getTextKo())) {
            return GenerationStatus.ALREADY_PRESENT;
        }

        if (attempts(play.getTextKoAttempts())
                >= properties.maxAttempts()) {
            return GenerationStatus.ATTEMPTS_EXHAUSTED;
        }

        String sourceText =
                play.getText().trim();

        String contextHash =
                AiContextHashCalculator
                        .calculatePlayTranslation(
                                gameId,
                                playId,
                                sourceText);

        AiPlayTranslationRequest request =
                new AiPlayTranslationRequest(
                        gameId,
                        playId,
                        MODE_REVEALED,
                        contextHash,
                        sourceText,
                        TARGET_LANGUAGE);

        Optional<AiPlayTranslationResponse> response =
                aiServiceClient.generatePlayTranslation(
                        request);

        /*
         * 실제 AI 호출을 수행했다면 결과와 관계없이 시도 횟수를 남긴다.
         * 이를 통해 실패·검수 반려 플레이가 scorer 주기마다
         * 무한히 재호출되는 것을 방지한다.
         */
        play.setTextKoAttempts(
                attempts(play.getTextKoAttempts()) + 1);
        playRepository.save(play);

        if (response.isEmpty()) {
            log.warn(
                    "AI 플레이 번역 호출 실패/타임아웃: gameId={} playId={}",
                    gameId,
                    playId);
            return GenerationStatus.CALL_FAILED;
        }

        AiPlayTranslationResponse result =
                response.orElseThrow();

        if (hasOpenAiFailure(result)) {
            log.warn(
                    "AI 플레이 번역 생성 실패: gameId={} playId={} violations={}",
                    gameId,
                    playId,
                    result.violations());
            return GenerationStatus.CALL_FAILED;
        }

        if (!isStorable(result)) {
            log.warn(
                    "AI 플레이 번역 검수 반려/저장 조건 불충족: "
                            + "gameId={} playId={} fallbackUsed={} "
                            + "translatedTextBlank={} contextHashMissing={} violations={}",
                    gameId,
                    playId,
                    result.fallbackUsed(),
                    !hasText(result.translatedText()),
                    !hasText(result.contextHash()),
                    result.violations());
            return GenerationStatus.REVIEW_REJECTED;
        }

        Play latest =
                playRepository.findById(playId)
                        .orElse(null);

        if (!isEligible(latest, gameId)
                || hasText(latest.getTextKo())) {
            return latest != null && hasText(latest.getTextKo())
                    ? GenerationStatus.ALREADY_PRESENT
                    : GenerationStatus.STALE;
        }

        String latestContextHash =
                AiContextHashCalculator
                        .calculatePlayTranslation(
                                gameId,
                                playId,
                                latest.getText().trim());

        if (!latestContextHash.equals(
                result.contextHash())) {
            log.info(
                    "AI 플레이 번역 stale 응답 폐기: gameId={} playId={}",
                    gameId,
                    playId);
            return GenerationStatus.STALE;
        }

        latest.setTextKo(
                result.translatedText().trim());
        latest.setTextKoContextHash(
                result.contextHash());

        playRepository.save(latest);
        liveSignalPublisher.publishGameSignal(
                gameId);

        return GenerationStatus.SAVED;
    }

    private static boolean isEligible(
            Play play,
            long gameId) {

        return play != null
                && play.getId() != null
                && play.getId() > 0
                && play.getGameId() != null
                && play.getGameId() == gameId
                && play.getType() != null
                && PLAY_RESULT_TYPE.equalsIgnoreCase(
                        play.getType().trim())
                && hasText(play.getText());
    }

    private static boolean isStorable(
            AiPlayTranslationResponse response) {

        return hasText(response.translatedText())
                && !response.fallbackUsed()
                && hasText(response.contextHash())
                && (response.violations() == null
                || response.violations().isEmpty());
    }

    private static boolean hasOpenAiFailure(
            AiPlayTranslationResponse response) {

        return response.violations() != null
                && response.violations().stream()
                        .filter(
                                violation -> violation != null)
                        .anyMatch(
                                violation -> violation.startsWith(
                                        "OPENAI_"));
    }

    private static int attempts(
            Integer value) {

        return value == null ? 0 : value;
    }

    private static boolean hasText(
            String value) {

        return value != null
                && !value.isBlank();
    }

    enum GenerationStatus {
        SAVED,
        ALREADY_PRESENT,
        NOT_ELIGIBLE,
        CALL_FAILED,
        REVIEW_REJECTED,
        STALE,
        ATTEMPTS_EXHAUSTED
    }
}
