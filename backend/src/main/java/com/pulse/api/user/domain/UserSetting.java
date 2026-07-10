package com.pulse.api.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_settings")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserSetting {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private Member member;

    @Column(name = "favorite_team_game_start_alert", nullable = false)
    @Builder.Default
    private boolean favoriteTeamGameStartAlert = true;

    @Column(name = "important_moment_alert", nullable = false)
    @Builder.Default
    private boolean importantMomentAlert = true;

    @Column(name = "game_switch_alert", nullable = false)
    @Builder.Default
    private boolean gameSwitchAlert = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /*
     * 알림 설정을 별도로 받지 않았을 때 사용하는 기본 설정 생성 메서드.
     *
     * 사용자가 회원가입 중 알림 설정 단계를 건너뛰거나,
     * 프론트에서 notificationSettings를 보내지 않은 경우 사용할 수 있다.
     *
     * 기본 정책:
     * - 관심팀 경기 시작 알림: ON
     * - 중요한 순간 알림: ON
     * - 경기 전환 추천 알림: ON
     */
    public static UserSetting createDefault(Member member) {
        return create(
                member,
                true,
                true,
                true
        );
    }

    /*
     * 회원가입 Step 3에서 사용자가 선택한 알림 설정을 반영해
     * UserSetting 엔티티를 생성하는 메서드.
     *
     * all 값은 프론트 UI용 전체 토글이므로 DB에 저장하지 않는다.
     *
     * 파라미터 매핑:
     * - gameStart  -> favorite_team_game_start_alert
     * - surge      -> important_moment_alert
     * - gameSwitch -> game_switch_alert
     */
    public static UserSetting create(
            Member member,
            boolean favoriteTeamGameStartAlert,
            boolean importantMomentAlert,
            boolean gameSwitchAlert
    ) {
        return UserSetting.builder()
                .member(member)
                .favoriteTeamGameStartAlert(favoriteTeamGameStartAlert)
                .importantMomentAlert(importantMomentAlert)
                .gameSwitchAlert(gameSwitchAlert)
                .build();
    }

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


    /*
     * 마이페이지 / 사용자 설정 화면에서 변경한 선호 설정을 반영한다.
     *
     * 이 메서드를 엔티티 내부에 두는 이유:
     * - UserSetting의 필드는 private이고 setter를 열지 않았기 때문
     * - 아무 곳에서나 필드를 막 바꾸는 것보다,
     *   "설정 수정"이라는 의미 있는 메서드로 변경하는 것이 안전하다.
     *
     * 파라미터 매핑:
     * - favoriteTeamGameStartAlert  -> favorite_team_game_start_alert
     * - importantMomentAlert        -> important_moment_alert
     * - gameSwitchAlert             -> game_switch_alert
     */
    public void updatePreferences(
            boolean favoriteTeamGameStartAlert,
            boolean importantMomentAlert,
            boolean gameSwitchAlert
    ) {
        this.favoriteTeamGameStartAlert = favoriteTeamGameStartAlert;
        this.importantMomentAlert = importantMomentAlert;
        this.gameSwitchAlert = gameSwitchAlert;
    }

}