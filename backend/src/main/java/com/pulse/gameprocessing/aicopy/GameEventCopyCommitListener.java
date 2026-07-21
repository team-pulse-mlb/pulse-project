package com.pulse.gameprocessing.aicopy;

import com.pulse.common.metrics.PulseMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** game_events와 하이라이트 저장 커밋 후 AI 문구 생성을 요청한다. */
@Component
@ConditionalOnProperty(prefix = "pulse.game-processor", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class GameEventCopyCommitListener {

    private final AiGenerationTrigger aiGenerationTrigger;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGameEventCopyRequested(GameEventCopyRequestedEvent event) {
        try {
            aiGenerationTrigger.onGameEventPersisted(
                    event.gameId(), event.eventId(), event.mode(), event.occurredAt());
        } catch (RuntimeException e) {
            PulseMetrics.increment("pulse.game-processor.gameeventcopy.failed");
            log.warn("경기 이벤트 문구 생성 요청 실패 gameId={} eventId={}",
                    event.gameId(), event.eventId(), e);
        }
    }
}
