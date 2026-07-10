package com.pulse.scorer;

import com.pulse.common.message.ScoreTask;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.poller.GameLifecycle;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameFinalizationService {

    private static final String FINALIZED_KEY_PREFIX = "score:finalized:";

    private final GameRepository gameRepository;
    private final LiveSignalPublisher liveSignalPublisher;
    private final AiGenerationTrigger aiGenerationTrigger;
    private final AfterCommitExecutor afterCommitExecutor;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public void handle(ScoreTask task) {
        Game game = gameRepository.findById(task.gameId()).orElse(null);
        if (game == null) {
            log.debug("종료 정리 대상 경기 없음: {}", task.gameId());
            return;
        }

        Instant observedAt = task.observedAt() == null ? Instant.now() : task.observedAt();
        Boolean firstProcessing = redisTemplate.opsForValue()
                .setIfAbsent(finalizedKey(task.gameId()), observedAt.toString());
        if (!Boolean.TRUE.equals(firstProcessing)) {
            log.debug("이미 종료 정리된 경기 skip: {}", task.gameId());
            return;
        }

        liveSignalPublisher.removeLiveGame(task.gameId());
        liveSignalPublisher.evictGameCache(task.gameId());
        liveSignalPublisher.publishGameSignal(task.gameId());
        liveSignalPublisher.publishRankingSignal();

        if (isFinal(task.lifecycleState())) {
            afterCommitExecutor.execute(() -> aiGenerationTrigger.onGameFinalized(task.gameId(), observedAt));
        }
        log.debug("경기 종료 정리 gameId={} lifecycleState={}", game.getId(), task.lifecycleState());
    }

    private static boolean isFinal(String lifecycleState) {
        return GameLifecycle.FINAL.name().equals(lifecycleState)
                || GameLifecycle.DONE.name().equals(lifecycleState);
    }

    private static String finalizedKey(long gameId) {
        return FINALIZED_KEY_PREFIX + gameId;
    }
}
