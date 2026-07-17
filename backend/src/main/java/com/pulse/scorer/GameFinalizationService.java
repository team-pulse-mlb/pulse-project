package com.pulse.scorer;

import com.pulse.common.message.ScoreTask;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.poller.GameLifecycle;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@Slf4j
public class GameFinalizationService {

    private static final String FINALIZED_KEY_PREFIX = "score:finalized:";
    private static final String TERMINAL_KEY_PREFIX = "score:terminal:";
    private static final String ARMED_KEY_PREFIX = "notify:armed:";
    private static final String COOLDOWN_KEY_PREFIX = "notify:cooldown:";

    private final GameRepository gameRepository;
    private final LiveSignalPublisher liveSignalPublisher;
    private final AiGenerationTrigger aiGenerationTrigger;
    private final AfterCommitExecutor afterCommitExecutor;
    private final TimelineHighlightBackfill timelineHighlightBackfill;
    private final StringRedisTemplate redisTemplate;
    private final Duration emergencyTtl;

    @Autowired
    public GameFinalizationService(
            GameRepository gameRepository,
            LiveSignalPublisher liveSignalPublisher,
            AiGenerationTrigger aiGenerationTrigger,
            AfterCommitExecutor afterCommitExecutor,
            TimelineHighlightBackfill timelineHighlightBackfill,
            StringRedisTemplate redisTemplate,
            @Value("${pulse.scorer.redis-emergency-ttl-ms:172800000}") long emergencyTtlMillis
    ) {
        this(
                gameRepository,
                liveSignalPublisher,
                aiGenerationTrigger,
                afterCommitExecutor,
                timelineHighlightBackfill,
                redisTemplate,
                Duration.ofMillis(emergencyTtlMillis)
        );
    }

    GameFinalizationService(
            GameRepository gameRepository,
            LiveSignalPublisher liveSignalPublisher,
            AiGenerationTrigger aiGenerationTrigger,
            AfterCommitExecutor afterCommitExecutor,
            TimelineHighlightBackfill timelineHighlightBackfill,
            StringRedisTemplate redisTemplate,
            Duration emergencyTtl
    ) {
        this.gameRepository = gameRepository;
        this.liveSignalPublisher = liveSignalPublisher;
        this.aiGenerationTrigger = aiGenerationTrigger;
        this.afterCommitExecutor = afterCommitExecutor;
        this.timelineHighlightBackfill = timelineHighlightBackfill;
        this.redisTemplate = redisTemplate;
        this.emergencyTtl = emergencyTtl;
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
        // 백필은 자체 멱등이므로 재전달에도 안전하며, 비FINAL 종료 키와 분리한다.
        if (finalGame) {
            timelineHighlightBackfill.backfillIfEmpty(task.gameId(), observedAt, true);
        }

        afterCommitExecutor.execute(() -> finalizeRedisState(task, observedAt, finalGame));
        log.debug("경기 종료 정리 gameId={} lifecycleState={}", game.getId(), task.lifecycleState());
    }

    private void finalizeRedisState(ScoreTask task, Instant observedAt, boolean finalGame) {
        redisTemplate.delete(List.of(armedKey(task.gameId()), cooldownKey(task.gameId())));

        String idempotencyKey = finalGame
                ? finalizedKey(task.gameId())
                : terminalKey(task.gameId(), task.lifecycleState());
        Boolean firstProcessing = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, observedAt.toString(), emergencyTtl);
        if (!Boolean.TRUE.equals(firstProcessing)) {
            // TTL 없이 남은 기존 멱등 키도 재전달 시 비상 TTL을 보정한다.
            redisTemplate.expire(idempotencyKey, emergencyTtl);
            log.debug("이미 종료 정리한 경기 skip: {}", task.gameId());
            return;
        }
        if (finalGame) {
            aiGenerationTrigger.onGameFinalized(task.gameId(), observedAt);
        }
    }

    private static boolean isFinal(String lifecycleState, Game game) {
        return GameLifecycle.FINAL.name().equals(lifecycleState) && game.isFinal();
    }

    private static String finalizedKey(long gameId) {
        return FINALIZED_KEY_PREFIX + gameId;
    }

    private static String terminalKey(long gameId, String lifecycleState) {
        return TERMINAL_KEY_PREFIX + gameId + ":" + lifecycleState;
    }

    private static String armedKey(long gameId) {
        return ARMED_KEY_PREFIX + gameId;
    }

    private static String cooldownKey(long gameId) {
        return COOLDOWN_KEY_PREFIX + gameId;
    }
}
