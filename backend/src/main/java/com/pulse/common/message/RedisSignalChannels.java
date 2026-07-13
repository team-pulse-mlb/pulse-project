package com.pulse.common.message;

/**
 * Redis pub/sub 재조회 신호 채널 정의. scorer가 발행하고 api가 SSE로 중계하는
 * 역할 간 계약이라 common에 둔다. payload에는 점수·결과 데이터를 싣지 않는다.
 */
public final class RedisSignalChannels {

    /** 랭킹 변경 신호 채널 */
    public static final String RANKING = "signal:ranking";

    /** 경기 갱신 신호 채널 접두사 (signal:game:{gameId}) */
    public static final String GAME_PREFIX = "signal:game:";

    /** 경기 갱신 신호 구독용 패턴 */
    public static final String GAME_PATTERN = GAME_PREFIX + "*";

    private RedisSignalChannels() {
    }

    public static String gameChannel(long gameId) {
        return GAME_PREFIX + gameId;
    }
}
