package com.pulse.scorer;

import com.pulse.common.metrics.PulseMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 라이브 점수 계산 커밋 후 SURGE를 판정하고 알림 outbox 생성을 요청한다. */
@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SurgeCommitListener {

    private final SurgeDetector surgeDetector;
    private final SurgeNotificationPublisher surgeNotificationPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLiveScoreComputed(LiveScoreComputedEvent event) {
        try {
            surgeDetector.evaluate(
                    event.gameId(),
                    event.watchScoreRounded(),
                    event.computedAt(),
                    () -> {
                        PulseMetrics.increment("pulse.scorer.surge.fired");
                        surgeNotificationPublisher.publish(
                                event.gameId(),
                                event.tags(),
                                event.previousTags(),
                                event.computedAt());
                    });
        } catch (RuntimeException e) {
            PulseMetrics.increment("pulse.scorer.surge.failed");
            log.warn("SURGE 커밋 후 처리 실패 gameId={} computedAt={}",
                    event.gameId(), event.computedAt(), e);
        }
    }
}
