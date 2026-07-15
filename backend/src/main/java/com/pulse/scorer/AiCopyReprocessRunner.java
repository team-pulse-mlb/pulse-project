package com.pulse.scorer;

import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.GameRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** 기존 값을 보존하면서 종료 헤드라인과 보호 이벤트 문구를 모두 다시 생성합니다. */
@Component
@Profile("ai-copy-reprocess")
@RequiredArgsConstructor
@Slf4j
class AiCopyReprocessRunner implements ApplicationRunner {

    private final AiCopyReprocessProperties properties;
    private final GameRepository gameRepository;
    private final GameEventRepository gameEventRepository;
    private final AiFinalHeadlineGenerator finalHeadlineGenerator;
    private final AiEventCopyGenerator eventCopyGenerator;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int headlineFailures = reprocessHeadlines();
            int eventFailures = reprocessEventCopies();
            int failures = headlineFailures + eventFailures;
            if (failures > 0) {
                throw new IllegalStateException("AI 문구 전체 재처리 실패: " + failures + "건");
            }
        } finally {
            applicationContext.close();
        }
    }

    private int reprocessHeadlines() {
        List<Long> gameIds = gameRepository.findAllFinalGameIds();
        EnumMap<AiFinalHeadlineGenerator.GenerationStatus, Integer> counts =
                new EnumMap<>(AiFinalHeadlineGenerator.GenerationStatus.class);
        int exceptions = 0;

        log.info("AI 종료 헤드라인 전체 재처리 시작: 대상={}건", gameIds.size());
        for (Long gameId : gameIds) {
            try {
                AiFinalHeadlineGenerator.GenerationStatus status =
                        finalHeadlineGenerator.regenerateSynchronously(gameId);
                counts.merge(status, 1, Integer::sum);
            } catch (RuntimeException exception) {
                exceptions++;
                log.warn("AI 종료 헤드라인 전체 재처리 예외: gameId={}", gameId, exception);
            }
        }

        int failures = count(counts, AiFinalHeadlineGenerator.GenerationStatus.NOT_GENERATED)
                + count(counts, AiFinalHeadlineGenerator.GenerationStatus.NOT_ELIGIBLE)
                + count(counts, AiFinalHeadlineGenerator.GenerationStatus.PARTIALLY_SAVED)
                + exceptions;
        log.info("AI 종료 헤드라인 전체 재처리 완료: 대상={}건 saved={} partial={} "
                        + "notGenerated={} notEligible={} exceptions={} failures={}",
                gameIds.size(),
                count(counts, AiFinalHeadlineGenerator.GenerationStatus.SAVED),
                count(counts, AiFinalHeadlineGenerator.GenerationStatus.PARTIALLY_SAVED),
                count(counts, AiFinalHeadlineGenerator.GenerationStatus.NOT_GENERATED),
                count(counts, AiFinalHeadlineGenerator.GenerationStatus.NOT_ELIGIBLE),
                exceptions,
                failures);
        return failures;
    }

    private int reprocessEventCopies() {
        long totalTargets = gameEventRepository.countBySpoilerLevel(GameEvent.SPOILER_PROTECTED_SAFE);
        long maxId = gameEventRepository.findMaxProtectedEventId();
        long afterId = 0;
        int targets = 0;
        int exceptions = 0;
        EnumMap<AiEventCopyGenerator.GenerationStatus, Integer> counts =
                new EnumMap<>(AiEventCopyGenerator.GenerationStatus.class);

        log.info("AI 보호 이벤트 문구 전체 재처리 시작: 대상={}건 maxEventId={} batchSize={}",
                totalTargets, maxId, properties.eventBatchSize());
        while (afterId < maxId) {
            List<GameEvent> batch = gameEventRepository.findProtectedAiReprocessTargets(
                    afterId,
                    maxId,
                    PageRequest.of(0, properties.eventBatchSize())
            );
            if (batch.isEmpty()) {
                break;
            }

            for (GameEvent event : batch) {
                try {
                    AiEventCopyGenerator.GenerationStatus status =
                            eventCopyGenerator.regenerateSynchronously(event.getGameId(), event.getId());
                    counts.merge(status, 1, Integer::sum);
                } catch (RuntimeException exception) {
                    exceptions++;
                    log.warn("AI 보호 이벤트 문구 전체 재처리 예외: gameId={} eventId={}",
                            event.getGameId(), event.getId(), exception);
                }
            }
            targets += batch.size();
            afterId = batch.get(batch.size() - 1).getId();
            log.info("AI 보호 이벤트 문구 전체 재처리 진행: processed={} lastEventId={}",
                    targets, afterId);
        }

        int failures = count(counts, AiEventCopyGenerator.GenerationStatus.CALL_FAILED)
                + count(counts, AiEventCopyGenerator.GenerationStatus.REVIEW_REJECTED)
                + count(counts, AiEventCopyGenerator.GenerationStatus.STALE)
                + count(counts, AiEventCopyGenerator.GenerationStatus.NOT_ELIGIBLE)
                + exceptions;
        log.info("AI 보호 이벤트 문구 전체 재처리 완료: 대상={}건 saved={} callFailed={} "
                        + "reviewRejected={} stale={} notEligible={} exceptions={} failures={}",
                targets,
                count(counts, AiEventCopyGenerator.GenerationStatus.SAVED),
                count(counts, AiEventCopyGenerator.GenerationStatus.CALL_FAILED),
                count(counts, AiEventCopyGenerator.GenerationStatus.REVIEW_REJECTED),
                count(counts, AiEventCopyGenerator.GenerationStatus.STALE),
                count(counts, AiEventCopyGenerator.GenerationStatus.NOT_ELIGIBLE),
                exceptions,
                failures);
        return failures;
    }

    private static <T extends Enum<T>> int count(Map<T, Integer> counts, T status) {
        return counts.getOrDefault(status, 0);
    }
}
