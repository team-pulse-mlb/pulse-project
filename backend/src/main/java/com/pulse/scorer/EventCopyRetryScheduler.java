package com.pulse.scorer;

import com.pulse.common.ai.AiCopyMode;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 최근 라이브 경기에서 누락된 AI 이벤트 문구를 제한된 횟수만큼 재시도한다. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
class EventCopyRetryScheduler {

    private final GameEventRepository gameEventRepository;
    private final AiEventCopyGenerator generator;
    private final EventCopyRetryProperties properties;
    private final Clock clock;

    @Autowired
    EventCopyRetryScheduler(
            GameEventRepository gameEventRepository,
            AiEventCopyGenerator generator,
            EventCopyRetryProperties properties
    ) {
        this(gameEventRepository, generator, properties, Clock.systemUTC());
    }

    EventCopyRetryScheduler(
            GameEventRepository gameEventRepository,
            AiEventCopyGenerator generator,
            EventCopyRetryProperties properties,
            Clock clock
    ) {
        this.gameEventRepository = gameEventRepository;
        this.generator = generator;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${pulse.ai.event-copy-retry.delay:180s}")
    public void retryMissingCopies() {
        Instant since = clock.instant().minus(properties.window());
        PageRequest batch = PageRequest.of(0, properties.batchSize());
        List<GameEvent> protectedTargets = gameEventRepository.findProtectedCopyRetryTargets(
                properties.maxAttempts(), since, batch);
        List<GameEvent> revealedTargets = gameEventRepository.findRevealedCopyRetryTargets(
                properties.maxAttempts(), since, batch);

        EnumMap<AiEventCopyGenerator.GenerationStatus, Integer> counts =
                new EnumMap<>(AiEventCopyGenerator.GenerationStatus.class);
        int exceptions = retry(protectedTargets, AiCopyMode.PROTECTED, counts)
                + retry(revealedTargets, AiCopyMode.REVEALED, counts);

        log.info("AI 이벤트 문구 재시도 완료: protectedTargets={} revealedTargets={} "
                        + "saved={} alreadyPresent={} notEligible={} callFailed={} "
                        + "reviewRejected={} stale={} exceptions={}",
                protectedTargets.size(),
                revealedTargets.size(),
                count(counts, AiEventCopyGenerator.GenerationStatus.SAVED),
                count(counts, AiEventCopyGenerator.GenerationStatus.ALREADY_PRESENT),
                count(counts, AiEventCopyGenerator.GenerationStatus.NOT_ELIGIBLE),
                count(counts, AiEventCopyGenerator.GenerationStatus.CALL_FAILED),
                count(counts, AiEventCopyGenerator.GenerationStatus.REVIEW_REJECTED),
                count(counts, AiEventCopyGenerator.GenerationStatus.STALE),
                exceptions);
    }

    private int retry(
            List<GameEvent> targets,
            AiCopyMode mode,
            Map<AiEventCopyGenerator.GenerationStatus, Integer> counts
    ) {
        int exceptions = 0;
        for (GameEvent event : targets) {
            try {
                AiEventCopyGenerator.GenerationStatus status = generator.generateSynchronously(
                        event.getGameId(), event.getId(), mode);
                counts.merge(status, 1, Integer::sum);
            } catch (RuntimeException exception) {
                exceptions++;
                log.warn("AI 이벤트 문구 재시도 예외: gameId={} eventId={} mode={}",
                        event.getGameId(), event.getId(), mode, exception);
            }
        }
        return exceptions;
    }

    private static int count(
            Map<AiEventCopyGenerator.GenerationStatus, Integer> counts,
            AiEventCopyGenerator.GenerationStatus status
    ) {
        return counts.getOrDefault(status, 0);
    }
}
