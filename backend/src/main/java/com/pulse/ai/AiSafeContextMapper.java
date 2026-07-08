package com.pulse.ai;

import com.pulse.api.GameQueryService;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class AiSafeContextMapper {

    public AiSafeContext toSafeContext(GameQueryService.SpoilerFreeLlmContextResponse response){
        //safeContext는 ai-service 요청의 필수 입력이므로 null이면 조용히 넘기지 않고 즉시 실패 시킴
        Objects.requireNonNull(response, "reponse must not be null");

        //Spring Boot 내부 조회 응답을 ai-service에 보낼 안전한 context 필드로만 변환한다
        //점수, 팀명, 선수명, recent play 원문, 승패/우세 정보는 포함하지 않는다.
        return new AiSafeContext(
                response.status(),
                response.periodLabel(),
                response.reasonTags(),
                response.spoilerSafeSignals()
        );
    }
}
