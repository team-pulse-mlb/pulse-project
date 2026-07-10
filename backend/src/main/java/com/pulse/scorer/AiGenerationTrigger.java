package com.pulse.scorer;

import java.time.Instant;

/**
 * 종료 경기 AI 문구 비동기 생성 트리거 포트.
 * 실구현은 창현(com.pulse.ai)이 제공하며, 없으면 no-op 스텁이 컴파일·동작을 유지한다.
 * 생성은 응답 경로 밖에서 이뤄지고 scorer는 시그니처만 의존한다.
 */
public interface AiGenerationTrigger {

    String MODE_PROTECTED = "PROTECTED";
    String MODE_REVEALED = "REVEALED";

    /** 경기 종료 시 최종 문구·마감 구간 요약 생성을 요청한다. */
    void onGameFinalized(long gameId, Instant occurredAt);

    /** game_events 영속 직후 이벤트 타임라인 문구 생성을 요청한다. */
    void onGameEventPersisted(long gameId, long eventId, String mode, Instant occurredAt);
}
