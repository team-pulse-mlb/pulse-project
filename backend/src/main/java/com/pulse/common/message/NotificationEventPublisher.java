package com.pulse.common.message;

import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.NotificationEventLog;
import com.pulse.domain.NotificationEventLogRepository;
import com.pulse.domain.NotificationOutbox;
import com.pulse.domain.NotificationOutboxRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationEventPublisher {

    private final NotificationEventLogRepository eventLogRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationOutboxDispatcher dispatcher;
    private final AfterCommitExecutor afterCommitExecutor;
    private final Clock clock;

    @Autowired
    public NotificationEventPublisher(
            NotificationEventLogRepository eventLogRepository,
            NotificationOutboxRepository outboxRepository,
            NotificationOutboxDispatcher dispatcher,
            AfterCommitExecutor afterCommitExecutor
    ) {
        this(eventLogRepository, outboxRepository, dispatcher, afterCommitExecutor, Clock.systemUTC());
    }

    NotificationEventPublisher(
            NotificationEventLogRepository eventLogRepository,
            NotificationOutboxRepository outboxRepository,
            NotificationOutboxDispatcher dispatcher,
            AfterCommitExecutor afterCommitExecutor,
            Clock clock
    ) {
        this.eventLogRepository = eventLogRepository;
        this.outboxRepository = outboxRepository;
        this.dispatcher = dispatcher;
        this.afterCommitExecutor = afterCommitExecutor;
        this.clock = clock;
    }

    @Transactional
    public void publish(NotificationEvent event) {
        publish(event, List.of());
    }

    @Transactional
    public void publish(NotificationEvent event, List<String> tags) {
        NotificationEventLog eventLog = new NotificationEventLog();
        eventLog.setEventId(event.eventId());
        eventLog.setType(event.type().name());
        eventLog.setGameId(event.gameId());
        eventLog.setTags(tags);
        eventLog.setOccurredAt(event.occurredAt());
        eventLogRepository.save(eventLog);
        outboxRepository.save(NotificationOutbox.pending(event, clock.instant()));
        afterCommitExecutor.execute(() -> dispatcher.publishEvent(event.eventId()));
    }
}
