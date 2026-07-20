package com.pulse.common.time;

import java.time.ZoneId;

/**
 * 홈 슬레이트 날짜 버킷과 시뮬레이터 날짜 게이트가 공유하는 KST 기준 시간대다.
 *
 * <p>경기의 소속 날짜는 시작 시각({@code start_time})의 KST 날짜로 정의하며,
 * 자정을 넘겨 종료해도 시작한 날짜의 슬레이트에 남는다.
 */
public final class SlateZone {
    /** 홈 슬레이트와 시뮬레이터 날짜 판단에 사용하는 한국 시간(KST). */
    public static final ZoneId ID = ZoneId.of("Asia/Seoul");

    private SlateZone() {}
}
