package com.pulse.ai;

import org.springframework.stereotype.Service;

@Service
public class DefaultAiCopyReader implements AiCopyReader {

    @Override
    public String getCopy(long gameId, AiCopyPurpose purpose) {
        return defaultCopy(purpose);
    }

    private String defaultCopy(AiCopyPurpose purpose) {
        if (purpose == null) {
            return "지금 볼 만한 흐름이 감지됐습니다.";
        }

        return switch (purpose) {
            case LIVE_HEADLINE -> "지금 볼 만한 흐름이 감지됐습니다.";
            case NOTIFICATION -> "지금 볼 만한 흐름의 경기가 감지됐습니다.";
            case SWITCH_SUGGESTION -> "다른 경기에서 긴장감이 높아졌습니다.";
            case REPLAY_SUMMARY -> "다시 볼 만한 흐름이 이어진 구간입니다.";
            case FINAL_HEADLINE -> "다시 볼 만한 흐름이 있었던 경기입니다.";
        };
    }
}
