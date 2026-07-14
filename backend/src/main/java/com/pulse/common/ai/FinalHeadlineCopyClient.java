package com.pulse.common.ai;

import java.util.Optional;

/**
 * 종료 경기 FINAL_HEADLINE AI 문구 생성을 요청하는 공통 인터페이스입니다.
 *
 * <p>이 인터페이스는 scorer/api 등 다른 모듈이 com.pulse.ai 구현체에 직접 의존하지 않도록
 * common 모듈에 둔 계약입니다.</p>
 *
 * <p>반환값이 {@link Optional#empty()}인 경우는 오류가 아니라
 * "현재 조건에서 AI 문구를 생성하지 않음" 또는 "ai-service 호출 실패"로 해석합니다.</p>
 */
public interface FinalHeadlineCopyClient {

    /**
     * 지정한 경기와 모드 기준으로 종료 경기 헤드라인 문구 생성을 요청합니다.
     *
     * @param gameId 경기 ID
     * @param mode PROTECTED 또는 REVEALED
     * @return AI 문구 생성·검수 결과. 생성 대상이 아니거나 호출 실패 시 Optional.empty()
     */
    Optional<AiCopyResult> generateFinalHeadline(long gameId, AiCopyMode mode);
}
