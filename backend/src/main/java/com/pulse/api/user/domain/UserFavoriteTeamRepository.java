package com.pulse.api.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface UserFavoriteTeamRepository
        extends JpaRepository<UserFavoriteTeam, UserFavoriteTeamId> {

    /**
     * 특정 사용자가 등록한 관심팀 목록을 조회합니다.
     *
     * 사용자 설정 화면에서 기존 관심팀을 보여줄 때 사용합니다.
     */
    List<UserFavoriteTeam> findByMemberUserId(Long userId);

    /**
     * 특정 사용자의 관심팀을 모두 삭제합니다.
     *
     * 현재 관심팀 수정 기능은 기존 목록 전체 삭제 후
     * 새 목록을 다시 저장하는 방식으로 구현되어 있습니다.
     */
    void deleteByMemberUserId(Long userId);

    /**
     * GAME_START 알림을 받을 사용자 목록을 조회합니다.
     *
     * 대상 조건:
     * 1. 홈팀 또는 원정팀을 관심팀으로 등록한 사용자
     * 2. 관심팀 경기 시작 알림 설정이 켜진 사용자
     * 3. 회원 상태가 ACTIVE인 사용자
     *
     * select distinct:
     * 한 사용자가 홈팀과 원정팀을 모두 관심팀으로 등록했더라도
     * 같은 경기 시작 알림은 한 번만 저장해야 합니다.
     *
     * join UserSetting us on us.member = uft.member:
     * 관심팀 정보와 사용자 알림 설정은 별도 테이블이므로,
     * 같은 Member를 기준으로 연결해서 설정값을 검사합니다.
     *
     * @param teamIds 홈팀 ID와 원정팀 ID
     * @param status  조회할 회원 상태. 실제 호출 시 MemberStatus.ACTIVE 사용
     * @return GAME_START 알림 대상 사용자 목록
     */
    @Query("""
            select distinct uft.member
            from UserFavoriteTeam uft
            join UserSetting us
              on us.member = uft.member
            where uft.team.teamId in :teamIds
              and us.favoriteTeamGameStartAlert = true
              and uft.member.status = :status
            """)
    List<Member> findGameStartNotificationTargets(
            @Param("teamIds") Collection<Long> teamIds,
            @Param("status") MemberStatus status
    );
}