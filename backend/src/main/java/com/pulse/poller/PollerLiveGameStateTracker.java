package com.pulse.poller;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class PollerLiveGameStateTracker {

    private final PollerProperties properties;
    private final Map<Long, Instant> lastTaskPublishedAt = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lastNewPlayAt = new ConcurrentHashMap<>();
    private final Map<Long, Instant> nextQuietPlaysPollAt = new ConcurrentHashMap<>();
    private final Map<Long, Integer> emptyFetchStreaks = new ConcurrentHashMap<>();
    private final Map<Long, Integer> recoveryStepBacks = new ConcurrentHashMap<>();

    PollerLiveGameStateTracker(PollerProperties properties) {
        this.properties = properties;
    }

    boolean heartbeatDue(long gameId, Instant now) {
        Instant lastPublishedAt = lastTaskPublishedAt.get(gameId);
        return lastPublishedAt == null
                || !now.isBefore(lastPublishedAt.plus(properties.heartbeatInterval()));
    }

    boolean quiet(long gameId, Instant now) {
        Instant lastNewPlay = lastNewPlayAt.computeIfAbsent(gameId, ignored -> now);
        return !now.isBefore(lastNewPlay.plus(properties.quietThreshold()));
    }

    boolean quietPollScheduledAfter(long gameId, Instant now) {
        Instant nextQuietPollAt = nextQuietPlaysPollAt.get(gameId);
        return nextQuietPollAt != null && now.isBefore(nextQuietPollAt);
    }

    void scheduleQuietPoll(long gameId, Instant now) {
        nextQuietPlaysPollAt.put(gameId, now.plus(properties.quietPlaysInterval()));
    }

    void markTaskPublished(long gameId, Instant now) {
        lastTaskPublishedAt.put(gameId, now);
    }

    void markNewPlays(long gameId, Instant now) {
        lastTaskPublishedAt.put(gameId, now);
        lastNewPlayAt.put(gameId, now);
        nextQuietPlaysPollAt.remove(gameId);
    }

    int incrementEmptyFetchStreak(long gameId) {
        return emptyFetchStreaks.merge(gameId, 1, Integer::sum);
    }

    void resetEmptyFetchStreak(long gameId) {
        emptyFetchStreaks.remove(gameId);
    }

    int incrementRecoveryStepBack(long gameId) {
        return recoveryStepBacks.merge(gameId, 1, Integer::sum);
    }

    void resetRecoveryStepBack(long gameId) {
        recoveryStepBacks.remove(gameId);
    }

    void clear(long gameId) {
        lastTaskPublishedAt.remove(gameId);
        lastNewPlayAt.remove(gameId);
        nextQuietPlaysPollAt.remove(gameId);
        emptyFetchStreaks.remove(gameId);
        recoveryStepBacks.remove(gameId);
    }
}
