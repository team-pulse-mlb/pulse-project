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

import java.time.Instant;

/**
 * 사용자의 알림 및 경기 전환 추천 설정을 저장하는 엔티티입니다.
 *
 * users 테이블과 user_id를 공유하는 1:1 관계입니다.
 *
 * DB 테이블:
 * user_settings
 */
@Entity
@Table(name = "user_settings")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserSetting {

    /**
     * users.user_id를 그대로 PK로 사용합니다.
     *
     * @MapsId가 적용되어 있으므로 Member의 userId와
     * UserSetting의 userId가 동일한 값을 갖습니다.
     */
    @Id
    @Column(name = "user_id")
    private Long userId;

    /**
     * 설정의 소유자인 회원입니다.
     *
     * UserSetting이 조회될 때 Member까지 항상 즉시 조회하지 않도록
     * LAZY 로딩을 사용합니다.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private Member member;

    /**
     * 관심팀 경기 시작 알림 여부입니다.
     *
     * Java 필드명은 기존 서비스와 프론트 계약을 유지하지만,
     * 실제 DB 컬럼명은 V7 스키마의 notify_game_start를 사용합니다.
     */
    @Column(name = "notify_game_start", nullable = false)
    @Builder.Default
    private boolean favoriteTeamGameStartAlert = true;

    /**
     * SURGE 등 중요한 순간 알림 여부입니다.
     *
     * V7 스키마:
     * notify_surge_enabled
     */
    @Column(name = "notify_surge_enabled", nullable = false)
    @Builder.Default
    private boolean importantMomentAlert = true;

    /**
     * 더 흥미로운 경기로 전환을 추천하는 기능의 사용 여부입니다.
     *
     * GAME_SWITCH는 저장 알림은 아니지만,
     * 사용자 추천 설정으로 user_settings에 보관합니다.
     *
     * V7 스키마:
     * recommend_switch_enabled
     */
    @Column(name = "recommend_switch_enabled", nullable = false)
    @Builder.Default
    private boolean gameSwitchAlert = true;

    /**
     * 설정 행이 최초 생성된 시각입니다.
     *
     * PostgreSQL TIMESTAMPTZ와 자연스럽게 대응하도록
     * Instant 타입을 사용합니다.
     */
    @Column(
            name = "created_at",
            updatable = false
    )
    private Instant createdAt;

    /**
     * 설정이 마지막으로 수정된 시각입니다.
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * 사용자가 알림 설정을 별도로 선택하지 않았을 때
     * 모든 기능을 켠 기본 설정을 생성합니다.
     *
     * 기본 정책:
     * - 관심팀 경기 시작 알림: ON
     * - 중요한 순간 알림: ON
     * - 경기 전환 추천: ON
     */
    public static UserSetting createDefault(Member member) {
        return create(
                member,
                true,
                true,
                true
        );
    }

    /**
     * 회원가입 또는 최초 설정 저장 시 사용할 생성 메서드입니다.
     *
     * 프론트 요청과 Java 필드의 매핑:
     * - gameStart  → favoriteTeamGameStartAlert
     * - surge      → importantMomentAlert
     * - gameSwitch → gameSwitchAlert
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

    /**
     * INSERT 직전에 생성 시각과 수정 시각을 기록합니다.
     */
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;
    }

    /**
     * UPDATE 직전에 마지막 수정 시각을 갱신합니다.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * 마이페이지 또는 설정 화면에서 전달된 값으로
     * 사용자의 설정을 변경합니다.
     *
     * setter를 외부에 공개하지 않고 의미 있는 변경 메서드를 두어
     * 설정 변경 경로를 제한합니다.
     */
    public void updatePreferences(
            boolean favoriteTeamGameStartAlert,
            boolean importantMomentAlert,
            boolean gameSwitchAlert
    ) {
        this.favoriteTeamGameStartAlert =
                favoriteTeamGameStartAlert;

        this.importantMomentAlert =
                importantMomentAlert;

        this.gameSwitchAlert =
                gameSwitchAlert;
    }
}