package com.pulse.scorer;

import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.AiCopyResult;
import com.pulse.common.ai.FinalHeadlineCopyClient;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 종료 경기 헤드라인 AI 문구 클라이언트를 scorer 종료 트리거에 연결한다. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
class AiFinalHeadlineGenerator {

    private final FinalHeadlineCopyClient finalHeadlineCopyClient;
    private final GameRepository gameRepository;
    private final LiveSignalPublisher liveSignalPublisher;

    /**
     * 종료 경기의 보호·공개 헤드라인을 각각 생성해 games 행에 저장한다.
     * 두 모드 모두 저장 대상이 아니면 DB 갱신과 시그널 발행을 건너뛴다.
     */
    @Async(AiGenerationAsyncConfig.TASK_EXECUTOR)
    public void generate(long gameId) {
        Optional<String> protectedTitle = storableTitle(gameId, AiCopyMode.PROTECTED);
        Optional<String> revealedTitle = storableTitle(gameId, AiCopyMode.REVEALED);
        if (protectedTitle.isEmpty() && revealedTitle.isEmpty()) {
            log.debug("AI 종료 헤드라인 저장 대상 없음: gameId={}", gameId);
            return;
        }

        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null || !game.isFinal()) {
            log.debug("AI 종료 헤드라인 저장 대상 경기 없음/미종료: gameId={}", gameId);
            return;
        }

        protectedTitle.ifPresent(game::setFinalHeadlineProtected);
        revealedTitle.ifPresent(game::setFinalHeadlineRevealed);
        gameRepository.save(game);
        liveSignalPublisher.publishGameSignal(gameId);
        liveSignalPublisher.publishRankingSignal();
    }

    private Optional<String> storableTitle(long gameId, AiCopyMode mode) {
        return finalHeadlineCopyClient.generateFinalHeadline(gameId, mode)
                .filter(AiFinalHeadlineGenerator::isStorable)
                .map(AiCopyResult::safeTitle);
    }

    private static boolean isStorable(AiCopyResult result) {
        return result.spoilerSafe()
                && !result.fallbackUsed()
                && result.contextHash() != null
                && result.safeTitle() != null
                && !result.safeTitle().isBlank();
    }
}
