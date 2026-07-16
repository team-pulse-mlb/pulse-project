package com.pulse.scorer;

import com.pulse.common.message.ScoreTask;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.poller.GameLifecycle;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class GameFinalizationService {

    private static final String FINALIZED_KEY_PREFIX = "score:finalized:";

    private final GameRepository gameRepository;
    private final LiveSignalPublisher liveSignalPublisher;
    private final AiGenerationTrigger aiGenerationTrigger;
    private final AfterCommitExecutor afterCommitExecutor;
    private final TimelineHighlightBackfill timelineHighlightBackfill;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public void handle(ScoreTask task) {
        Game game = gameRepository.findById(task.gameId()).orElse(null);
        if (game == null) {
            log.debug("종료 정리 대상 경기 없음: {}", task.gameId());
            return;
        }

        // Redis 정리는 멱등이므로 종료 task가 재전달될 때마다 재시도한다.
        liveSignalPublisher.removeLiveGame(task.gameId());
        liveSignalPublisher.evictGameCache(task.gameId());
        liveSignalPublisher.publishGameSignal(task.gameId());
        liveSignalPublisher.publishRankingSignal();

        Instant observedAt = task.observedAt() == null ? Instant.now() : task.observedAt();
        // 백필은 자체 멱등이므로 재전달에도 안전하며, 비FINAL 선처리로 선점될 수 있는 종료 키에 종속시키지 않는다.
        if (isFinal(task.lifecycleState(), game)) {
            timelineHighlightBackfill.backfillIfEmpty(task.gameId(), observedAt, true);
        }

        Boolean firstProcessing = redisTemplate.opsForValue()
                .setIfAbsent(finalizedKey(task.gameId()), observedAt.toString());
        if (!Boolean.TRUE.equals(firstProcessing)) {
            log.debug("이미 종료 정리된 경기 skip: {}", task.gameId());
            return;
        }

        if (isFinal(task.lifecycleState(), game)) {
            afterCommitExecutor.execute(() -> aiGenerationTrigger.onGameFinalized(task.gameId(), observedAt));
        }
        log.debug("경기 종료 정리 gameId={} lifecycleState={}", game.getId(), task.lifecycleState());
    }

    private static boolean isFinal(String lifecycleState, Game game) {
        return GameLifecycle.FINAL.name().equals(lifecycleState) && game.isFinal();
    }

    private static String finalizedKey(long gameId) {
        return FINALIZED_KEY_PREFIX + gameId;
    }
}
