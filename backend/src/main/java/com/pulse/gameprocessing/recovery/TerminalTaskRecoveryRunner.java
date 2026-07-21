package com.pulse.gameprocessing.recovery;

import com.pulse.gameprocessing.application.GameFinalizationService;
import com.pulse.common.message.ScoreTaskFactory;
import com.pulse.common.message.ScoreTaskPublisher;
import com.pulse.domain.Game;
import com.pulse.domain.GameLifecycle;
import com.pulse.domain.GameRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("${pulse.scorer.enabled:true}"
        + " and ${pulse.score-task-outbox.scheduler-enabled:true}")
@Slf4j
public class TerminalTaskRecoveryRunner {

    private final GameRepository gameRepository;
    private final GameFinalizationService gameFinalizationService;
    private final ScoreTaskFactory scoreTaskFactory;
    private final ScoreTaskPublisher scoreTaskPublisher;
    private final Duration recoveryWindow;
    private final Clock clock;

    @Autowired
    public TerminalTaskRecoveryRunner(
            GameRepository gameRepository,
            GameFinalizationService gameFinalizationService,
            ScoreTaskFactory scoreTaskFactory,
            ScoreTaskPublisher scoreTaskPublisher,
            @Value("${pulse.scorer.finalization-recovery-window-ms:86400000}") long recoveryWindowMillis
    ) {
        this(
                gameRepository,
                gameFinalizationService,
                scoreTaskFactory,
                scoreTaskPublisher,
                Duration.ofMillis(recoveryWindowMillis),
                Clock.systemUTC()
        );
    }

    TerminalTaskRecoveryRunner(
            GameRepository gameRepository,
            GameFinalizationService gameFinalizationService,
            ScoreTaskFactory scoreTaskFactory,
            ScoreTaskPublisher scoreTaskPublisher,
            Duration recoveryWindow,
            Clock clock
    ) {
        this.gameRepository = gameRepository;
        this.gameFinalizationService = gameFinalizationService;
        this.scoreTaskFactory = scoreTaskFactory;
        this.scoreTaskPublisher = scoreTaskPublisher;
        this.recoveryWindow = recoveryWindow;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${pulse.scorer.finalization-recovery-delay-ms:900000}",
            initialDelayString = "${pulse.scorer.finalization-recovery-delay-ms:900000}"
    )
    public void recover() {
        Instant now = clock.instant();
        // 시즌 누적 terminal 경기 전체를 매번 스캔하지 않도록 최근 갱신 경기로 한정한다.
        // finalized 키에 비상 TTL이 붙어도 TTL(48h)보다 짧은 창(기본 24h)이라 과거 경기가 재발행되지 않는다.
        List<Game> terminalGames = gameRepository.findByLifecycleStateInAndUpdatedAtAfter(
                List.of(GameLifecycle.FINAL.name(), GameLifecycle.DONE.name()),
                now.minus(recoveryWindow)
        );
        for (Game game : terminalGames) {
            try {
                // score:finalized 키는 종료 정리 경로의 중복 방지 지점 도달 여부를 나타내는 명시적 기록이다.
                // 랭킹 항목은 재생성될 수 있고 헤드라인은 생성 실패가 가능하므로 완료 판정 기준으로 쓰지 않는다.
                if (!gameFinalizationService.hasFinalizationRecord(game.getId())) {
                    scoreTaskPublisher.publish(scoreTaskFactory.terminalTask(game, now));
                    log.info("누락된 종료 task 재발행: gameId={}", game.getId());
                }
            } catch (RuntimeException e) {
                log.error("종료 task 복구 실패: gameId={}", game.getId(), e);
            }
        }
    }
}
