package com.pulse.api.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserSettingRepository
        extends JpaRepository<UserSetting, Long> {

    /**
     * SURGE 알림을 받을 사용자를 조회합니다.
     *
     * 대상 조건:
     * 1. 중요한 순간 알림 설정이 켜져 있어야 합니다.
     * 2. 회원 상태가 ACTIVE여야 합니다.
     *
     * SURGE 알림은 특정 관심팀에 한정하지 않고
     * 설정을 켠 정상 회원 전체를 대상으로 합니다.
     *
     * select us.member:
     * - UserSetting 자체가 아니라 연결된 Member를 반환합니다.
     *
     * join을 직접 작성하지 않아도:
     * - us.member.status처럼 연관관계의 필드로 접근할 수 있습니다.
     *
     * @param status 조회할 회원 상태. 실제 호출 시 MemberStatus.ACTIVE 사용
     * @return SURGE 알림 대상 사용자 목록
     */
    @Query("""
            select us.member
            from UserSetting us
            where us.importantMomentAlert = true
              and us.member.status = :status
            """)
    List<Member> findImportantMomentNotificationTargets(
            @Param("status") MemberStatus status
    );
}