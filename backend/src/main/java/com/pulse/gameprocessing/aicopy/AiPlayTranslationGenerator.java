package com.pulse.gameprocessing.aicopy;

import com.pulse.gameprocessing.effect.LiveSignalPublisher;
import com.pulse.ai.AiPlayTranslationRequest;
import com.pulse.ai.AiPlayTranslationResponse;
import com.pulse.ai.AiServiceClient;
import com.pulse.common.ai.AiContextHashCalculator;
import com.pulse.common.ai.AiCopyContextReader;
import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.FinalHeadlineContext;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import java.time.Instant;
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

    /**
     * 번역 저장 후 REVEALED FINAL_HEADLINE을 다시 생성할 가치가 있는
     * 핵심 경기 흐름 태그입니다.
     *
     * 단순 안타나 일반 득점만으로는 재생성하지 않습니다.
     */
    private static final Set<String>
            FINAL_HEADLINE_REGENERATION_FACT_TAGS =
            Set.of(
                    "DECISIVE_SCORE",
                    "TYING_SCORE",
                    "LEAD_CHANGE",
                    "TAKES_LEAD",
                    "INSURANCE_SCORE",
                    "CUTS_DEFICIT",
                    "COMEBACK_WIN",
                    "WALK_OFF"
            );

    private final AiServiceClient aiServiceClient;
    private final PlayRepository playRepository;
    private final LiveSignalPublisher liveSignalPublisher;
    private final PlayTranslationProperties properties;

    private final AiCopyContextReader aiCopyContextReader;
    private final GameRepository gameRepository;
    private final AiFinalHeadlineGenerator aiFinalHeadlineGenerator;

    /**
     * 연속 ScoreTask가 들어와도 같은 play를 동시에 번역하지 않도록
     * 현재 JVM에서 처리 중인 내부 play ID를 기록한다.
     */
    private final Set<Long> inFlightPlayIds =
            ConcurrentHashMap.newKeySet();

    /**
     * game processor 메시지 소비 스레드를 막지 않고 미번역 타석 결과를 처리한다.
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

        boolean translationSaved = false;

        for (Play play : pending) {
            Long playId = play.getId();

            if (playId == null
                    || playId <= 0
                    || !inFlightPlayIds.add(playId)) {
                continue;
            }

            try {
                GenerationStatus status =
                        generateSynchronously(
                                gameId,
                                playId);

                if (status == GenerationStatus.SAVED) {
                    translationSaved = true;
                }
            } finally {
                inFlightPlayIds.remove(playId);
            }
        }

        /*
         * 현재 batch의 번역 저장이 모두 끝난 뒤 context를 다시 조회합니다.
         * 번역된 중요 플레이가 있으면 DB claim을 획득한 한 호출만
         * REVEALED FINAL_HEADLINE을 재생성합니다.
         */
        if (translationSaved) {
            regenerateRevealedHeadlineIfReady(gameId);
        }
    }

    /**
     * 번역된 중요 verifiedPlay가 context에 반영된 경우에만
     * REVEALED FINAL_HEADLINE의 일회성 자동 재생성을 시도합니다.
     */
    private void regenerateRevealedHeadlineIfReady(
            long gameId
    ) {
        Optional<FinalHeadlineContext> context =
                aiCopyContextReader.finalHeadlineContext(
                        gameId,
                        AiCopyMode.REVEALED
                );

        if (context.isEmpty()
                || !containsTranslatedImportantPlay(
                        context.orElseThrow()
                )) {
            return;
        }

        int acquired =
                gameRepository
                        .markFinalHeadlineRevealedRegenerationAttempted(
                                gameId,
                                Instant.now()
                        );

        if (acquired != 1) {
            log.debug(
                    "AI 공개 헤드라인 자동 재생성 이미 시도됨: "
                            + "gameId={}",
                    gameId
            );
            return;
        }

        AiFinalHeadlineGenerator.GenerationStatus status =
                aiFinalHeadlineGenerator
                        .regenerateRevealedSynchronously(
                                gameId
                        );

        log.info(
                "AI 공개 헤드라인 번역 반영 자동 재생성 완료: "
                        + "gameId={} status={}",
                gameId,
                status
        );
    }

    /**
     * verifiedPlay에 번역문과 핵심 경기 흐름 태그가
     * 함께 존재하는지 확인합니다.
     */
    private static boolean containsTranslatedImportantPlay(
            FinalHeadlineContext context
    ) {
        return context.verifiedPlays().stream()
                .filter(play -> play != null)
                .filter(
                        play -> hasText(
                                play.translatedText()
                        )
                )
                .map(
                        FinalHeadlineContext
                                .VerifiedPlay
                                ::factTags
                )
                .filter(tags -> tags != null)
                .anyMatch(
                        tags -> tags.stream()
                                .anyMatch(
                                        FINAL_HEADLINE_REGENERATION_FACT_TAGS
                                                ::contains
                                )
                );
    }

    GenerationStatus generateSynchronously(
            long gameId,
            long playId) {

        return generateSynchronously(
                gameId,
                playId,
                false);
    }

    /** 기존 번역이 있어도 다시 생성하고 검수 통과 결과만 덮어씁니다. */
    GenerationStatus regenerateSynchronously(
            long gameId,
            long playId) {

        return generateSynchronously(
                gameId,
                playId,
                true);
    }

    private GenerationStatus generateSynchronously(
            long gameId,
            long playId,
            boolean force) {

        Play play =
                playRepository.findById(playId)
                        .orElse(null);

        if (!isEligible(play, gameId)) {
            return GenerationStatus.NOT_ELIGIBLE;
        }

        if (!force
                && hasText(play.getTextKo())) {
            return GenerationStatus.ALREADY_PRESENT;
        }

        if (!force
                && attempts(play.getTextKoAttempts())
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
         * 이를 통해 실패·검수 반려 플레이가 game processor 주기마다
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

        if (!isEligible(latest, gameId)) {
            return GenerationStatus.STALE;
        }

        if (!force
                && hasText(latest.getTextKo())) {
            return GenerationStatus.ALREADY_PRESENT;
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
