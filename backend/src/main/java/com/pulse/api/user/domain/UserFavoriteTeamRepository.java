package com.pulse.api.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserFavoriteTeamRepository
        extends JpaRepository<UserFavoriteTeam, UserFavoriteTeamId> {

    List<UserFavoriteTeam> findByMemberUserId(Long userId);

    void deleteByMemberUserId(Long userId);
}