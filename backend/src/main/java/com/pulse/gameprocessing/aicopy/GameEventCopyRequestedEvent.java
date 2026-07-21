package com.pulse.gameprocessing.aicopy;

import java.time.Instant;

/** game_events 저장 트랜잭션 커밋 후 실행할 AI 문구 생성 요청. */
public record GameEventCopyRequestedEvent(
        long gameId,
        long eventId,
        String mode,
        Instant occurredAt
) {
}
