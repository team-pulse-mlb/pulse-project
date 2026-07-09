package com.pulse.ai;

public enum AiCopyMode {
    PROTECTED,
    REVEALED;

    public static AiCopyMode defaultIfNull(AiCopyMode mode) {
        // mode가 명시되지 않으면 스포일러 방지를 위해 보호 모드를 기본값으로 사용한다.
        return mode == null ? PROTECTED : mode;
    }
}