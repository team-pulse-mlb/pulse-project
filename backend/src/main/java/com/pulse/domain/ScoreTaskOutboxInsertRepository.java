package com.pulse.domain;

public interface ScoreTaskOutboxInsertRepository {

    boolean insertPending(ScoreTaskOutbox outbox);
}
