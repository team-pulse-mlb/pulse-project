package com.pulse.ai;

import java.util.List;

/**
 * ai-service의 POST /ai/play-translation 응답 계약이다.
 *
 * translatedText가 없거나 fallbackUsed=true이면
 * 후속 저장 단계에서 번역 실패로 처리한다.
 */
public record AiPlayTranslationResponse(
        String translatedText,
        List<String> violations,
        boolean fallbackUsed,
        String contextHash
) {
}
