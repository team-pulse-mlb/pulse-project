package com.pulse.api.user.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 사용자의 관심 선수 정보를 조회·저장·삭제하는 Repository입니다.
 */
public interface UserFavoritePlayerRepository
        extends JpaRepository<
        UserFavoritePlayer,
        UserFavoritePlayerId
        > {

    /**
     * 특정 사용자가 등록한 관심 선수 목록을
     * 등록 순서대로 조회합니다.
     *
     * player를 함께 조회해서 응답 DTO 변환 과정에서
     * 선수마다 추가 쿼리가 발생하는 문제를 줄입니다.
     */
    @EntityGraph(attributePaths = "player")
    List<UserFavoritePlayer>
    findByMemberUserIdOrderByCreatedAtAsc(Long userId);
}