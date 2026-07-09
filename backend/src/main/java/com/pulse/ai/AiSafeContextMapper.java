package com.pulse.ai;

import com.pulse.api.GameQueryService;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class AiSafeContextMapper {

    public AiSafeContext toSafeContext(GameQueryService.SpoilerFreeLlmContextResponse response) {
        // safeContext는 ai-service 요청의 필수 입력이므로 null이면 즉시 실패시킨다.
        Objects.requireNonNull(response, "response must not be null");

        // Spring Boot 내부 조회 응답에서 ai-service에 전달 가능한 안전 필드만 추출한다.
        // 점수, 팀명, 선수명, recent play 원문, 승패/우세 정보는 포함하지 않는다.
        return new AiSafeContext(
                response.status(),
                response.periodLabel(),
                response.reasonTags(),
                response.spoilerSafeSignals()
        );
    }
}