package com.pulse.ai;

/**
 * ai-service의 POST /ai/play-translation 요청 계약이다.
 *
 * 공개 모드 최근 플레이 번역에는 단일 Play Result 원문만 전달하며,
 * 점수·팀·추천 점수·다른 플레이 문맥은 포함하지 않는다.
 */
public record AiPlayTranslationRequest(
        long gameId,
        long playId,
        String mode,
        String contextHash,
        String sourceText,
        String targetLanguage
) {
}
