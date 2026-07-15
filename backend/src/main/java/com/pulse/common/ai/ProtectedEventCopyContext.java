package com.pulse.common.ai;

import java.util.List;
import java.util.Map;

/**
 * 보호 모드 이벤트 문구 생성 컨텍스트.
 *
 * 기본 라벨 외에 스포일러-세이프한 상황 근거를 함께 제공해, 같은 라벨의 이벤트라도
 * 문구가 구분되게 한다. {@code situation}에는 점수·결과가 아닌 카운트·주자·투구수만,
 * {@code contributingLabels}에는 같은 이닝의 보호 라벨 묶음만 담는다.
 */
public record ProtectedEventCopyContext(
        long gameId,
        long eventId,
        String eventType,
        String label,
        Integer inning,
        List<String> contributingLabels,
        Map<String, Object> situation,
        String contextHash
) implements EventCopyContext {
}
