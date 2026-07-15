package com.pulse.api.user.dto;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UserPreferenceUpdateRequest {

    /*
     * 사용자가 새로 선택한 관심팀 ID 목록.
     *
     * 수정 API에서는 "전체 교체" 방식으로 처리한다.
     *
     * 예:
     * 기존 관심팀: [147, 119]
     * 요청 관심팀: [110]
     *
     * 결과:
     * 기존 관심팀 삭제 후 [110]만 저장
     */
    private List<Long> selectedTeamIds = new ArrayList<>();

    /*
     * 사용자 알림 설정.
     *
     * null로 요청이 들어올 경우를 대비해 기본값 객체를 둔다.
     */
    @Valid
    private NotificationSettingsRequest notificationSettings =
            new NotificationSettingsRequest();


    @Getter
    @Setter
    @NoArgsConstructor
    public static class NotificationSettingsRequest {

        /*
         * 전체 알림 토글.
         *
         * 프론트 UI용 값이므로 DB에는 직접 저장하지 않는다.
         */
        private boolean all = true;

        /*
         * 관심팀 경기 시작 알림.
         */
        private boolean gameStart = true;

        /*
         * 중요한 순간 / 모멘텀 급상승 알림.
         */
        private boolean surge = true;

        /*
         * 경기 전환 추천 알림.
         */
        private boolean gameSwitch = true;
    }

    /*
     * 사용자가 선택한 관심 선수 ID 목록입니다.
     *
     * 처리 규칙:
     * - null: 기존 관심 선수 설정을 그대로 유지
     * - 빈 배열 []: 관심 선수를 모두 해제
     * - [208, ...]: 전달된 선수 목록으로 교체
     *
     * 현재 프론트의 관심팀·알림 저장 요청에는 아직 selectedPlayerIds가 없으므로,
     * 필드를 기본 빈 배열로 만들지 않고 null을 허용합니다.
     *
     * 이렇게 해야 기존 관심팀이나 알림 설정을 수정했을 때
     * 관심 선수 목록이 의도치 않게 모두 삭제되는 문제를 막을 수 있습니다.
     */
    private List<Long> selectedPlayerIds;
}