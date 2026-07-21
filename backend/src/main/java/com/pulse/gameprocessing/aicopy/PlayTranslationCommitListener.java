package com.pulse.gameprocessing.aicopy;

import com.pulse.gameprocessing.event.LiveScoreComputedEvent;
import com.pulse.common.metrics.PulseMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 라이브 점수 계산 커밋 후 미번역 플레이 결과 생성을 요청하는 리스너.
 * 커밋 스레드에서 동기 실행되므로 예외를 잡아 실패 메트릭·로그로 격리하고
 * 다른 커밋 후 부수효과로 전파되지 않게 한다.
 */
@Component
@ConditionalOnProperty(prefix = "pulse.game-processor", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PlayTranslationCommitListener {

    private final AiGenerationTrigger aiGenerationTrigger;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLiveScoreComputed(LiveScoreComputedEvent event) {
        try {
            /*
             * 한 poll에 여러 play가 저장될 수 있으므로 마지막 관측 순서까지의
             * 미번역 타석 결과를 비동기로 생성한다.
             */
            aiGenerationTrigger.onPlayTranslationsPending(
                    event.gameId(),
                    event.translationThroughPlayOrder(),
                    event.computedAt());
        } catch (RuntimeException e) {
            PulseMetrics.increment("pulse.game-processor.playtranslation.failed");
            log.warn("미번역 플레이 생성 요청 실패 gameId={} computedAt={}",
                    event.gameId(), event.computedAt(), e);
        }
    }
}
