package com.pulse.gameprocessing.effect;

import com.pulse.gameprocessing.event.LiveScoreComputedEvent;
import com.pulse.common.metrics.PulseMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 라이브 점수 계산 커밋 후 Redis 반영(랭킹 ZSET·경기 HASH 캐시·재조회 신호)을 수행하는 리스너.
 * 커밋 스레드에서 동기 실행되므로 예외를 잡아 실패 메트릭·로그로 격리하고
 * 다른 커밋 후 부수효과로 전파되지 않게 한다.
 */
@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class LiveRedisProjectionCommitListener {

    private final LiveSignalPublisher liveSignalPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLiveScoreComputed(LiveScoreComputedEvent event) {
        try {
            liveSignalPublisher.publishLiveUpdate(
                    event.gameId(),
                    event.watchScore(),
                    event.baseScoreRounded(),
                    event.tags(),
                    event.inning(),
                    event.inningType(),
                    event.scoredPlayOrder(),
                    event.lifecycleState(),
                    event.previousTags(),
                    event.computedAt());
        } catch (RuntimeException e) {
            PulseMetrics.increment("pulse.scorer.projection.failed");
            log.warn("Redis 라이브 반영 실패 gameId={} computedAt={}",
                    event.gameId(), event.computedAt(), e);
        }
    }
}
