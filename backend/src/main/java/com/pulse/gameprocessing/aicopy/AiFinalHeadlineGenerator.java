package com.pulse.gameprocessing.aicopy;

import com.pulse.gameprocessing.effect.LiveSignalPublisher;
import com.pulse.common.ai.AiCopyContextReader;
import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.AiCopyResult;
import com.pulse.common.ai.FinalHeadlineContext;
import com.pulse.common.ai.FinalHeadlineCopyClient;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 종료 경기 FINAL_HEADLINE AI 문구를 생성하고 저장하는 game processor 측 생성기입니다.
 *
 * <p>중요한 책임:</p>
 * <ul>
 *     <li>PROTECTED / REVEALED 두 모드를 독립적으로 요청합니다.</li>
 *     <li>ai-service 응답이 저장 조건을 만족할 때만 Game headline 컬럼에 반영합니다.</li>
 *     <li>저장 직전 최신 contextHash를 다시 조회하여 stale response 저장을 막습니다.</li>
 *     <li>저장 성공 시 프론트 재조회용 game/ranking signal을 발행합니다.</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
class AiFinalHeadlineGenerator {

    private final FinalHeadlineCopyClient finalHeadlineCopyClient;
    private final AiCopyContextReader aiCopyContextReader;
    private final GameRepository gameRepository;
    private final LiveSignalPublisher liveSignalPublisher;

    /**
     * 종료 경기 헤드라인 생성을 수행합니다.
     *
     * <p>한 mode의 생성 실패가 다른 mode 저장을 막지 않도록
     * PROTECTED와 REVEALED 결과를 독립적으로 처리합니다.</p>
     */
    @Async(AiGenerationAsyncConfig.TASK_EXECUTOR)
    public void generate(long gameId) {
        generateSynchronously(gameId);
    }

    /**
     * 라이브 종료 처리와 일회성 백필이 공유하는 동기 생성 경로입니다.
     * 이미 저장된 모드는 다시 호출하지 않아 재실행 시 비용과 문구 변경을 막습니다.
     */
    GenerationStatus generateSynchronously(long gameId) {
        return generateSynchronously(gameId, false);
    }

    /** 기존 문구가 있어도 양쪽 모드를 다시 생성하고 성공한 결과만 덮어씁니다. */
    GenerationStatus regenerateSynchronously(long gameId) {
        return generateSynchronously(gameId, true);
    }

    /**
     * 중요 플레이 번역 반영용 REVEALED 전용 재생성 경로입니다.
     *
     * <p>PROTECTED 헤드라인은 다시 요청하거나 변경하지 않습니다.</p>
     */
    GenerationStatus regenerateRevealedSynchronously(
            long gameId
    ) {
        Game current =
                gameRepository.findById(gameId)
                        .orElse(null);

        if (current == null || !current.isFinal()) {
            log.debug(
                    "AI 공개 헤드라인 재생성 대상 경기 없음/미종료: "
                            + "gameId={}",
                    gameId
            );

            return GenerationStatus.NOT_ELIGIBLE;
        }

        Optional<AiCopyResult> revealedResult =
                requestStorableHeadline(
                        gameId,
                        AiCopyMode.REVEALED
                );

        if (revealedResult.isEmpty()) {
            log.debug(
                    "AI 공개 헤드라인 재생성 결과 없음: gameId={}",
                    gameId
            );

            return GenerationStatus.NOT_GENERATED;
        }

        Game latest =
                gameRepository.findById(gameId)
                        .orElse(null);

        if (latest == null || !latest.isFinal()) {
            log.debug(
                    "AI 공개 헤드라인 재생성 저장 대상 없음/미종료: "
                            + "gameId={}",
                    gameId
            );

            return GenerationStatus.NOT_ELIGIBLE;
        }

        revealedResult = filterLatestContextHash(
                gameId,
                AiCopyMode.REVEALED,
                revealedResult
        );

        if (revealedResult.isEmpty()) {
            log.debug(
                    "AI 공개 헤드라인 재생성 contextHash 불일치: "
                            + "gameId={}",
                    gameId
            );

            return GenerationStatus.NOT_GENERATED;
        }

        latest.setFinalHeadlineRevealed(
                revealedResult.orElseThrow().safeTitle()
        );

        gameRepository.save(latest);

        liveSignalPublisher.publishGameSignal(gameId);
        liveSignalPublisher.publishRankingSignal();

        return GenerationStatus.SAVED;
    }

    private GenerationStatus generateSynchronously(long gameId, boolean force) {
        Game current = gameRepository.findById(gameId).orElse(null);
        if (current == null || !current.isFinal()) {
            log.debug("AI 종료 헤드라인 저장 대상 경기 없음/미종료: gameId={}", gameId);
            return GenerationStatus.NOT_ELIGIBLE;
        }

        boolean needsProtected = force || isBlank(current.getFinalHeadlineProtected());
        boolean needsRevealed = force || isBlank(current.getFinalHeadlineRevealed());
        if (!needsProtected && !needsRevealed) {
            log.debug("AI 종료 헤드라인 이미 저장됨: gameId={}", gameId);
            return GenerationStatus.ALREADY_PRESENT;
        }

        Optional<AiCopyResult> protectedResult = needsProtected
                ? requestStorableHeadline(gameId, AiCopyMode.PROTECTED)
                : Optional.empty();

        Optional<AiCopyResult> revealedResult = needsRevealed
                ? requestStorableHeadline(gameId, AiCopyMode.REVEALED)
                : Optional.empty();

        if (protectedResult.isEmpty() && revealedResult.isEmpty()) {
            log.debug("AI 종료 헤드라인 저장 대상 없음: gameId={}", gameId);
            return GenerationStatus.NOT_GENERATED;
        }

        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null || !game.isFinal()) {
            log.debug("AI 종료 헤드라인 저장 대상 경기 없음/미종료: gameId={}", gameId);
            return GenerationStatus.NOT_ELIGIBLE;
        }

        protectedResult = filterLatestContextHash(
                gameId,
                AiCopyMode.PROTECTED,
                protectedResult
        );
        revealedResult = filterLatestContextHash(
                gameId,
                AiCopyMode.REVEALED,
                revealedResult
        );

        if (protectedResult.isEmpty() && revealedResult.isEmpty()) {
            log.debug("AI 종료 헤드라인 최신 contextHash 불일치로 저장 생략: gameId={}", gameId);
            return GenerationStatus.NOT_GENERATED;
        }

        int requestedCount = (needsProtected ? 1 : 0) + (needsRevealed ? 1 : 0);
        int generatedCount = (protectedResult.isPresent() ? 1 : 0) + (revealedResult.isPresent() ? 1 : 0);
        boolean changed = false;
        if ((force || isBlank(game.getFinalHeadlineProtected())) && protectedResult.isPresent()) {
            game.setFinalHeadlineProtected(protectedResult.orElseThrow().safeTitle());
            changed = true;
        }
        if ((force || isBlank(game.getFinalHeadlineRevealed())) && revealedResult.isPresent()) {
            game.setFinalHeadlineRevealed(revealedResult.orElseThrow().safeTitle());
            changed = true;
        }
        if (!changed) {
            log.debug("AI 종료 헤드라인 동시 저장으로 갱신 생략: gameId={}", gameId);
            return GenerationStatus.ALREADY_PRESENT;
        }

        gameRepository.save(game);
        liveSignalPublisher.publishGameSignal(gameId);
        liveSignalPublisher.publishRankingSignal();
        return generatedCount == requestedCount
                ? GenerationStatus.SAVED
                : GenerationStatus.PARTIALLY_SAVED;
    }

    private Optional<AiCopyResult> requestStorableHeadline(long gameId, AiCopyMode mode) {
        Optional<AiCopyResult> response = finalHeadlineCopyClient.generateFinalHeadline(gameId, mode);
        if (response.isEmpty()) {
            log.warn("AI 종료 헤드라인 호출 실패/응답 없음: gameId={} mode={}", gameId, mode);
            return Optional.empty();
        }

        AiCopyResult result = response.orElseThrow();
        if (hasViolation(result, "OPENAI_TIMEOUT")) {
            log.warn("AI 종료 헤드라인 호출 타임아웃: gameId={} mode={} violations={}",
                    gameId, mode, result.violations());
            return Optional.empty();
        }
        if (hasOpenAiFailure(result)) {
            log.warn("AI 종료 헤드라인 생성 실패: gameId={} mode={} violations={}",
                    gameId, mode, result.violations());
            return Optional.empty();
        }
        if (!isStorable(result)) {
            log.warn("AI 종료 헤드라인 검수 반려/저장 조건 불충족: gameId={} mode={} "
                            + "spoilerSafe={} fallbackUsed={} safeTitleBlank={} "
                            + "contextHashMissing={} violations={}",
                    gameId,
                    mode,
                    result.spoilerSafe(),
                    result.fallbackUsed(),
                    isBlank(result.safeTitle()),
                    result.contextHash() == null,
                    result.violations());
            return Optional.empty();
        }
        return response;
    }

    private static boolean hasOpenAiFailure(AiCopyResult result) {
        return result.violations().stream()
                .filter(violation -> violation != null)
                .anyMatch(violation -> violation.startsWith("OPENAI_"));
    }

    private static boolean hasViolation(AiCopyResult result, String expected) {
        return result.violations().contains(expected);
    }

    /** ai-service 응답이 저장 가능한 형태인지 1차 검증합니다. */
    private static boolean isStorable(AiCopyResult result) {
        return result.spoilerSafe()
                && !result.fallbackUsed()
                && result.contextHash() != null
                && result.safeTitle() != null
                && !result.safeTitle().isBlank();
    }

    /**
     * 저장 직전 최신 contextHash와 응답 contextHash가 일치하는지 검증합니다.
     *
     * <p>응답이 늦게 도착했거나, 중간에 경기 context가 바뀐 경우
     * stale response가 최신 문구를 덮어쓰지 못하게 저장을 중단합니다.</p>
     */
    private Optional<AiCopyResult> filterLatestContextHash(
            long gameId,
            AiCopyMode mode,
            Optional<AiCopyResult> result
    ) {
        if (result.isEmpty()) {
            return result;
        }

        AiCopyResult candidate = result.orElseThrow();
        String latestContextHash = aiCopyContextReader
                .finalHeadlineContext(gameId, mode)
                .map(FinalHeadlineContext::contextHash)
                .orElse(null);
        if (candidate.contextHash().equals(latestContextHash)) {
            return result;
        }

        log.info("AI 종료 헤드라인 stale 응답 폐기: gameId={} mode={} latestContextMissing={}",
                gameId, mode, latestContextHash == null);
        return Optional.empty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    enum GenerationStatus {
        SAVED,
        PARTIALLY_SAVED,
        ALREADY_PRESENT,
        NOT_GENERATED,
        NOT_ELIGIBLE
    }
}
