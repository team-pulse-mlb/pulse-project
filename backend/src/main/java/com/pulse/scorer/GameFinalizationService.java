package com.pulse.scorer;

import com.pulse.common.message.ScoreTask;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.poller.GameLifecycle;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@Slf4j
public class GameFinalizationService {

    private static final String ARMED_KEY_PREFIX = "notify:armed:";
    private static final String COOLDOWN_KEY_PREFIX = "notify:cooldown:";

    private final GameRepository gameRepository;
    private final LiveSignalPublisher liveSignalPublisher;
    private final AiGenerationTrigger aiGenerationTrigger;
    private final AfterCommitExecutor afterCommitExecutor;
    private final TimelineHighlightBackfill timelineHighlightBackfill;
    private final StringRedisTemplate redisTemplate;

    public GameFinalizationService(
            GameRepository gameRepository,
            LiveSignalPublisher liveSignalPublisher,
            AiGenerationTrigger aiGenerationTrigger,
            AfterCommitExecutor afterCommitExecutor,
            TimelineHighlightBackfill timelineHighlightBackfill,
            StringRedisTemplate redisTemplate
    ) {
        this.gameRepository = gameRepository;
        this.liveSignalPublisher = liveSignalPublisher;
        this.aiGenerationTrigger = aiGenerationTrigger;
        this.afterCommitExecutor = afterCommitExecutor;
        this.timelineHighlightBackfill = timelineHighlightBackfill;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public void handle(ScoreTask task) {
        Game game = gameRepository.findById(task.gameId()).orElse(null);
        if (game == null) {
            log.debug("종료 정리 대상 경기 없음: {}", task.gameId());
            return;
        }

        liveSignalPublisher.removeLiveGame(task.gameId());
        liveSignalPublisher.evictGameCache(task.gameId());
        liveSignalPublisher.publishGameSignal(task.gameId());
        liveSignalPublisher.publishRankingSignal();

        Instant observedAt = task.observedAt() == null ? Instant.now() : task.observedAt();
        boolean finalGame = isFinal(task.lifecycleState(), game);
        boolean firstProcessing = claimFirstProcessing(task, observedAt, finalGame);
        if (finalGame && firstProcessing) {
            timelineHighlightBackfill.backfillIfEmpty(task.gameId(), observedAt, true);
        }

        afterCommitExecutor.execute(() -> finalizeAfterCommit(task, observedAt, finalGame, firstProcessing));
        log.debug("경기 종료 정리 gameId={} lifecycleState={}", game.getId(), task.lifecycleState());
    }

    private boolean claimFirstProcessing(ScoreTask task, Instant observedAt, boolean finalGame) {
        int updatedRows;
        if (finalGame) {
            updatedRows = gameRepository.markFinalized(task.gameId(), observedAt);
        } else if (GameLifecycle.DONE.name().equals(task.lifecycleState())) {
            updatedRows = gameRepository.markDone(task.gameId(), observedAt);
        } else if (GameLifecycle.SUSPENDED_POSTPONED.name().equals(task.lifecycleState())) {
            updatedRows = gameRepository.markSuspendedPostponed(task.gameId(), observedAt);
        } else {
            log.debug("DB 종료 처리 기록 대상이 아닌 task skip: gameId={} lifecycleState={}",
                    task.gameId(), task.lifecycleState());
            return false;
        }
        return updatedRows == 1;
    }

    private void finalizeAfterCommit(
            ScoreTask task,
            Instant observedAt,
            boolean finalGame,
            boolean firstProcessing
    ) {
        // armed·cooldown은 알림 빈도 제어용 캐시이므로 반복 task에서도 정리한다.
        redisTemplate.delete(List.of(armedKey(task.gameId()), cooldownKey(task.gameId())));
        if (!firstProcessing) {
            log.debug("이미 종료 정리한 경기 skip: {}", task.gameId());
            return;
        }
        if (finalGame) {
            aiGenerationTrigger.onGameFinalized(task.gameId(), observedAt);
        }
    }

    /**
     * 종료 후처리 완료 여부를 DB 기록으로 판정한다(복구 러너용).
     * lifecycle별 기록을 보므로, POSTPONED만 처리된 경기가 재개 후 FINAL 재발행 대상에서 빠지지 않는다.
     */
    public boolean hasFinalizationRecord(long gameId) {
        return gameRepository.findById(gameId)
                .map(game -> GameLifecycle.DONE.name().equals(game.getLifecycleState())
                        ? game.getTerminalDoneAt() != null
                        : game.getFinalizedAt() != null)
                .orElse(true); // 경기 자체가 없으면 재발행 대상이 아니다.
    }

    private static boolean isFinal(String lifecycleState, Game game) {
        return GameLifecycle.FINAL.name().equals(lifecycleState) && game.isFinal();
    }

    private static String armedKey(long gameId) {
        return ARMED_KEY_PREFIX + gameId;
    }

    private static String cooldownKey(long gameId) {
        return COOLDOWN_KEY_PREFIX + gameId;
    }
}
