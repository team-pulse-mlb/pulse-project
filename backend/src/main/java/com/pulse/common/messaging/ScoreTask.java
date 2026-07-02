package com.pulse.common.messaging;

/**
 * poller가 발행하고 scorer가 소비하는 점수 재계산 요청.
 */
public record ScoreTask(long gameId) {
}
