package com.pulse.scorer;

import com.pulse.scoring.ImportanceCalculator;
import com.pulse.scoring.ScoreCalculator;
import com.pulse.scoring.ScoringInput;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.ScoreTask;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.ranking.RankingService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 리팩토링 안전망: 라이브 파이프라인의 외부 I/O(Redis 랭킹·HASH·pub/sub)가
 * DB 커밋 이후에만 반영되고 롤백 시 전혀 반영되지 않음을 파이프라인 수준에서 고정한다.
 *
 * 실제 {@link LiveSignalPublisher} + {@link AfterCommitExecutor}를 물리고
 * {@link TransactionSynchronizationManager}로 커밋/롤백 위상을 수동 구동한다.
 */
class LiveScoringServiceCommitPhaseTest {

    private static final long GAME_ID = 5059180L;
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-17T01:00:00Z");
    private static final double WATCH_SCORE = 80.0;

    private final ScoringProperties properties = TestScoringProperties.version5();
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final PlayRepository playRepository = mock(PlayRepository.class);
    private final WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);
    private final ScoreCalculator calculator = mock(ScoreCalculator.class);
    private final ImportanceCalculator importanceCalculator = mock(ImportanceCalculator.class);
    private final RankingService rankingService = mock(RankingService.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
    private final LiveSignalPublisher liveSignalPublisher =
            new LiveSignalPublisher(rankingService, redisTemplate, new AfterCommitExecutor());
    private final LiveScoringService service = new LiveScoringService(
            gameRepository,
            playRepository,
            watchScoreRepository,
            calculator,
            importanceCalculator,
            mock(GameEventExtractor.class),
            liveSignalPublisher,
            mock(SurgeDetector.class),
            mock(TimelineHighlightTrigger.class),
            mock(AiGenerationTrigger.class),
            mock(SurgeNotificationPublisher.class),
            properties);

    LiveScoringServiceCommitPhaseTest() {
        when(watchScoreRepository.existsByGameIdAndComputedAt(GAME_ID, OBSERVED_AT)).thenReturn(false);
        when(watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(GAME_ID))
                .thenReturn(Optional.empty());
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(liveGame()));
        when(importanceCalculator.multiplier(any(Game.class))).thenReturn(1.0);
        when(calculator.calculate(any(ScoringInput.class)))
                .thenReturn(new ScoreCalculator.Result(Map.of(), 60.0, false, 1.0, 0, WATCH_SCORE));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @AfterEach
    void cleanUpTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void handle_shouldApplyRedisProjectionOnlyAfterCommit() {
        beginTransaction();

        service.handle(liveTask());

        // 커밋 전에는 랭킹·캐시·신호 어느 것도 반영되지 않는다.
        verifyNoInteractions(rankingService);
        verify(hashOperations, never()).putAll(anyString(), anyMap());
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(rankingService).updateLive(GAME_ID, WATCH_SCORE);
        verify(hashOperations).putAll(eq("game:" + GAME_ID + ":live"), anyMap());
        verify(redisTemplate).convertAndSend("signal:game:" + GAME_ID, String.valueOf(GAME_ID));
        verify(redisTemplate).convertAndSend("signal:ranking", "changed");
    }

    @Test
    void handle_shouldNotApplyRedisProjectionWhenTransactionRollsBack() {
        beginTransaction();

        service.handle(liveTask());
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        // 롤백이면 커밋된 파생 데이터가 없으므로 외부 I/O도 전혀 실행되지 않는다.
        verifyNoInteractions(rankingService);
        verify(hashOperations, never()).putAll(anyString(), anyMap());
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    private static ScoreTask liveTask() {
        return new ScoreTask(GAME_ID, OBSERVED_AT, null, "LIVE", null);
    }

    private static Game liveGame() {
        Game game = new Game();
        game.setId(GAME_ID);
        game.setStatus(Game.STATUS_IN_PROGRESS);
        game.setLifecycleState("LIVE");
        game.setPeriod(8);
        game.setHomeRuns(3);
        game.setAwayRuns(2);
        return game;
    }

    private static void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void completeTransaction(int status) {
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
                synchronization.afterCommit();
            }
            synchronization.afterCompletion(status);
        }
    }
}
