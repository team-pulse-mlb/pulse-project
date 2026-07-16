package com.pulse.scorer;

import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

/** 종료 경기의 하이라이트와 AI 문구를 단계별 설정에 따라 다시 생성합니다. */
@Component
@Profile("ai-copy-reprocess")
@RequiredArgsConstructor
@Slf4j
class AiCopyReprocessRunner implements ApplicationRunner {

    private static final ZoneId WINDOW_ZONE = ZoneId.of("Asia/Seoul");

    private final AiCopyReprocessProperties properties;
    private final GameRepository gameRepository;
    private final GameEventRepository gameEventRepository;
    private final PlayRepository playRepository;
    private final AiFinalHeadlineGenerator finalHeadlineGenerator;
    private final AiEventCopyGenerator eventCopyGenerator;
    private final AiPlayTranslationGenerator playTranslationGenerator;
    private final TimelineHighlightBackfill timelineHighlightBackfill;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Long> windowedGameIds = properties.hasWindow() ? resolveWindowedGameIds() : null;
            if (windowedGameIds != null) {
                log.info("AI 문구 재처리 기간 한정: start={} end={}(포함) 대상 경기={}건",
                        properties.windowStartDate(), properties.windowEndDate(), windowedGameIds.size());
            }
            List<Long> targetGameIds =
                    windowedGameIds != null ? windowedGameIds : gameRepository.findAllFinalGameIds();
            int backfillFailures = 0;
            if (properties.runBackfill()) {
                backfillFailures = backfillTimelineHighlights(targetGameIds);
            } else {
                log.info("타임라인 하이라이트 삭제 후 재생성 단계 스킵");
            }

            int headlineFailures = 0;
            if (properties.runHeadline()) {
                headlineFailures = reprocessHeadlines(targetGameIds);
            } else {
                log.info("AI 종료 헤드라인 재처리 단계 스킵");
            }

            int eventFailures = 0;
            if (properties.runEventCopy()) {
                eventFailures = reprocessEventCopies(windowedGameIds);
            } else {
                log.info("AI 보호 이벤트 문구 재처리 단계 스킵");
            }

            int playTranslationFailures = 0;
            if (properties.runPlayTranslation()) {
                playTranslationFailures = reprocessPlayTranslations(targetGameIds);
            } else {
                log.info("AI 플레이 번역 재처리 단계 스킵");
            }
            int failures = backfillFailures + headlineFailures + eventFailures + playTranslationFailures;
            if (failures > 0) {
                throw new IllegalStateException("AI 문구 전체 재처리 실패: " + failures + "건");
            }
        } finally {
            applicationContext.close();
        }
    }

    private int backfillTimelineHighlights(List<Long> gameIds) {
        int marked = 0;
        int exceptions = 0;

        log.info("타임라인 하이라이트 삭제 후 재생성 시작: 대상 경기={}건", gameIds.size());
        for (Long gameId : gameIds) {
            try {
                marked += timelineHighlightBackfill.rebuildHighlights(gameId, Instant.now(), false);
            } catch (RuntimeException exception) {
                exceptions++;
                log.warn("타임라인 하이라이트 삭제 후 재생성 예외: gameId={}", gameId, exception);
            }
        }
        log.info("타임라인 하이라이트 삭제 후 재생성 완료: 대상 경기={}건 표시={}건 exceptions={}",
                gameIds.size(), marked, exceptions);
        return exceptions;
    }

    private List<Long> resolveWindowedGameIds() {
        Instant startInclusive = LocalDate.parse(properties.windowStartDate())
                .atStartOfDay(WINDOW_ZONE)
                .toInstant();
        Instant endExclusive = LocalDate.parse(properties.windowEndDate())
                .plusDays(1)
                .atStartOfDay(WINDOW_ZONE)
                .toInstant();
        return gameRepository.findFinalGameIdsByStartTimeBetween(startInclusive, endExclusive);
    }

    private int reprocessHeadlines(List<Long> gameIds) {
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

    private int reprocessEventCopies(List<Long> windowedGameIds) {
        int targets;
        int exceptions = 0;
        EnumMap<AiEventCopyGenerator.GenerationStatus, Integer> counts =
                new EnumMap<>(AiEventCopyGenerator.GenerationStatus.class);

        if (windowedGameIds != null) {
            List<GameEvent> events = windowedGameIds.isEmpty()
                    ? List.of()
                    : gameEventRepository
                            .findBySpoilerLevelAndTimelineHighlightTrueAndGameIdInOrderByGameIdAscObservedAtAsc(
                            GameEvent.SPOILER_PROTECTED_SAFE, windowedGameIds);
            log.info("AI 보호 이벤트 문구 기간 한정 재처리 시작: 대상={}건", events.size());
            for (GameEvent event : events) {
                try {
                    AiEventCopyGenerator.GenerationStatus status =
                            eventCopyGenerator.regenerateSynchronously(event.getGameId(), event.getId());
                    counts.merge(status, 1, Integer::sum);
                } catch (RuntimeException exception) {
                    exceptions++;
                    log.warn("AI 보호 이벤트 문구 기간 한정 재처리 예외: gameId={} eventId={}",
                            event.getGameId(), event.getId(), exception);
                }
            }
            targets = events.size();
        } else {
            long totalTargets = gameEventRepository.countBySpoilerLevelAndTimelineHighlightTrue(
                    GameEvent.SPOILER_PROTECTED_SAFE);
            long maxId = gameEventRepository.findMaxProtectedEventId();
            long afterId = 0;
            targets = 0;

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

    private int reprocessPlayTranslations(List<Long> gameIds) {
        int targets = 0;
        int exceptions = 0;
        EnumMap<AiPlayTranslationGenerator.GenerationStatus, Integer> counts =
                new EnumMap<>(AiPlayTranslationGenerator.GenerationStatus.class);

        log.info("AI 플레이 번역 전체 재처리 시작: 대상 경기={}건", gameIds.size());
        for (Long gameId : gameIds) {
            List<Play> plays = playRepository.findPlayTranslationReprocessTargets(gameId);
            targets += plays.size();
            for (Play play : plays) {
                try {
                    AiPlayTranslationGenerator.GenerationStatus status =
                            playTranslationGenerator.regenerateSynchronously(gameId, play.getId());
                    counts.merge(status, 1, Integer::sum);
                } catch (RuntimeException exception) {
                    exceptions++;
                    log.warn("AI 플레이 번역 전체 재처리 예외: gameId={} playId={}",
                            gameId, play.getId(), exception);
                }
            }
        }

        int failures = count(counts, AiPlayTranslationGenerator.GenerationStatus.CALL_FAILED)
                + count(counts, AiPlayTranslationGenerator.GenerationStatus.REVIEW_REJECTED)
                + count(counts, AiPlayTranslationGenerator.GenerationStatus.STALE)
                + count(counts, AiPlayTranslationGenerator.GenerationStatus.NOT_ELIGIBLE)
                + exceptions;
        log.info("AI 플레이 번역 전체 재처리 완료: 대상={}건 saved={} callFailed={} "
                        + "reviewRejected={} stale={} notEligible={} exceptions={} failures={}",
                targets,
                count(counts, AiPlayTranslationGenerator.GenerationStatus.SAVED),
                count(counts, AiPlayTranslationGenerator.GenerationStatus.CALL_FAILED),
                count(counts, AiPlayTranslationGenerator.GenerationStatus.REVIEW_REJECTED),
                count(counts, AiPlayTranslationGenerator.GenerationStatus.STALE),
                count(counts, AiPlayTranslationGenerator.GenerationStatus.NOT_ELIGIBLE),
                exceptions,
                failures);
        return failures;
    }

    private static <T extends Enum<T>> int count(Map<T, Integer> counts, T status) {
        return counts.getOrDefault(status, 0);
    }
}
