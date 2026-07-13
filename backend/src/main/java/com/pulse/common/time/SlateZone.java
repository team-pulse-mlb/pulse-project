package com.pulse.common.time;

import java.time.ZoneId;

/**
 * 홈 슬레이트 날짜 버킷과 시뮬레이터 날짜 게이트가 공유하는 기준 시간대다.
 *
 * <p>슬레이트를 날짜로 나누는 기준(홈)과, 시뮬레이터가 "오늘 노출 대상"을 판단하는 기준이
 * 어긋나면 UTC·ET 자정 경계에서 카드가 다른 날짜로 새는 문제가 생긴다. 두 곳이 항상 같은
 * 시간대를 쓰도록 이 상수를 단일 기준으로 참조한다.
 */
public final class SlateZone {
    /** 미국 동부 시간(ET). MLB 경기 편성 기준일과 맞춘다. */
    public static final ZoneId ID = ZoneId.of("America/New_York");

    private SlateZone() {}
}
