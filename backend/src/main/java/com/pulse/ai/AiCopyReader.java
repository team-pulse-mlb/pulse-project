package com.pulse.ai;

public interface AiCopyReader {

    String getCopy(long gameId, AiCopyPurpose purpose, AiCopyMode mode);

    default String getCopy(long gameId, AiCopyPurpose purpose) {
        //기존 호출부가 생겨도 보호모드 기본 문구를 반환하도록 호환용 기본 메서드를 둔다
        return getCopy(gameId, purpose, AiCopyMode.PROTECTED);
    }
}
