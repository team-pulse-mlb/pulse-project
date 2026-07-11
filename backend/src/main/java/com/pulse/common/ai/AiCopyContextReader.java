package com.pulse.common.ai;

import java.util.Optional;

/**
 * AI 문구 생성용 safeContext 조회 계약(모듈 인터페이스, 예은 → 창현).
 * 구현은 com.pulse.api.AiCopyContextService가 제공하며, 창현 모듈(com.pulse.ai)이
 * 반환값을 ai-service 요청으로 변환·전송한다. 빈 값은 "생성 대상 아님"이며 오류가 아니다.
 * 상세 규칙은 docs/design/AI_COPY.md §4.0 참고.
 */
public interface AiCopyContextReader {

    Optional<FinalHeadlineContext> finalHeadlineContext(long gameId, AiCopyMode mode);

    Optional<EventCopyContext> eventCopyContext(long gameId, long eventId, AiCopyMode mode);
}
