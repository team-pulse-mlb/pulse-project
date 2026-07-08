package com.pulse.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEventLogRepository extends JpaRepository<NotificationEventLog, UUID> {
}
