package com.pulse.api.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SignupRequest {

    /*
     * React에서 보내는 회원가입 JSON을 받는 DTO.
     *
     * 현재 프론트 SignupPage는 3단계 구조다.
     *
     * Step 1: 이메일, 이메일 인증번호, 비밀번호, 비밀번호 확인
     * Step 2: 관심팀 선택
     * Step 3: 알림 설정
     *
     * 실제 회원 저장은 마지막 단계에서 한 번만 요청된다.
     */

    /*
     * 로그인 ID로 사용할 이메일.
     *
     * 프론트에서 1차 검증을 하더라도,
     * 개발자도구나 Postman으로 잘못된 요청을 보낼 수 있기 때문에
     * 서버에서도 반드시 검증한다.
     */
    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "올바른 이메일 형식으로 입력해 주세요.")
    private String email;

    /*
     * 사용자가 입력한 원문 비밀번호.
     *
     * DB에는 절대 원문을 저장하지 않고,
     * MemberService에서 BCrypt로 암호화한 passwordHash만 저장한다.
     */
    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(
            min = 8,
            max = 64,
            message = "비밀번호는 8자 이상 64자 이하로 입력해 주세요."
    )
    private String password;

    /*
     * 사용자가 선택한 관심팀 ID 목록.
     *
     * 프론트 타입:
     * selectedTeamIds: number[]
     *
     * DB 저장 위치:
     * user_favorite_teams.user_id
     * user_favorite_teams.team_id
     *
     * 건너뛰기를 허용하기 위해 null 대신 빈 리스트를 기본값으로 둔다.
     * 최대 3팀 제한은 DTO 검증보다 Service에서 명확히 검사할 예정이다.
     */
    private List<Long> selectedTeamIds = new ArrayList<>();

    /*
     * 알림 설정 객체.
     *
     * 프론트 타입:
     * notificationSettings: {
     *   all: boolean,
     *   gameStart: boolean,
     *   surge: boolean,
     *   gameSwitch: boolean
     * }
     *
     * @Valid를 붙이면 내부 클래스의 검증 어노테이션도 동작한다.
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
         * 프론트 UI에서 개별 토글을 한 번에 켜고 끄기 위한 값이다.
         * DB에는 저장하지 않는다.
         */
        private boolean all = true;

        /*
         * 관심팀 경기 시작 알림.
         *
         * DB 매핑:
         * user_settings.favorite_team_game_start_alert
         */
        private boolean gameStart = true;

        /*
         * 중요한 순간 / 모멘텀 급상승 알림.
         *
         * DB 매핑:
         * user_settings.important_moment_alert
         */
        private boolean surge = true;

        /*
         * 더 볼만한 경기로 전환 추천 알림.
         *
         * DB 매핑:
         * user_settings.game_switch_alert
         */
        private boolean gameSwitch = true;
    }
}