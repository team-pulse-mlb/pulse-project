package com.pulse.api.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(
            name = "email",
            nullable = false,
            unique = true,
            length = 255
    )
    private String email;

    /*
     * 회원가입 요청에서는 일반 비밀번호를 받지만,
     * DB에는 BCrypt로 암호화한 값만 저장한다.
     */
    @Column(
            name = "password_hash",
            nullable = false,
            length = 255
    )
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "role",
            nullable = false,
            length = 20
    )
    @Builder.Default
    private MemberRole role = MemberRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    @Builder.Default
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;

    /*
     * 실제 DELETE를 하지 않고 탈퇴 시각을 기록한다.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


    /**
     * 회원의 비밀번호 해시를 새로운 값으로 변경합니다.
     *
     * 이 메서드에는 사용자가 입력한 원문 비밀번호가 아니라
     * PasswordEncoder로 암호화가 끝난 BCrypt 해시만 전달해야 합니다.
     *
     * 비밀번호 필드를 직접 수정하지 않고 도메인 메서드로 변경하는 이유:
     * - 비밀번호 변경 규칙을 Member 엔티티 내부로 제한할 수 있습니다.
     * - 원문 비밀번호를 실수로 저장하는 위험을 줄일 수 있습니다.
     * - 이후 비밀번호 변경 이력이나 추가 정책이 생기면 이 메서드에서 관리할 수 있습니다.
     */
    public void changePasswordHash(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException(
                    "새 비밀번호 해시는 비어 있을 수 없습니다."
            );
        }

        this.passwordHash = newPasswordHash;
    }


    /**
     * 회원을 Soft Delete 상태로 변경합니다.
     *
     * DB row는 삭제하지 않고,
     * 상태와 탈퇴 시각만 기록합니다.
     */
    public void withdraw() {
        if (this.status == MemberStatus.WITHDRAWN) {
            throw new IllegalStateException(
                    "이미 탈퇴한 회원입니다."
            );
        }

        this.status = MemberStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
    }
}