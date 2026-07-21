package com.pulse.scorer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 라이브 점수 이벤트의 커밋 후 소비자 골격.
 * 현재는 검증용 no-op이며, 이후 단계에서 AI 번역·Redis projection·SURGE fan-out이 이 리스너로 이동한다.
 */
@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@Slf4j
public class LiveScoreComputedEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLiveScoreComputed(LiveScoreComputedEvent event) {
        log.debug("라이브 점수 이벤트 수신 gameId={} computedAt={}", event.gameId(), event.computedAt());
    }
}
