package com.pulse.api.user.domain;

import com.pulse.domain.Team;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_favorite_teams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFavoriteTeam {

    @EmbeddedId
    private UserFavoriteTeamId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("teamId")
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private UserFavoriteTeam(Member member, Team team) {
        this.member = member;
        this.team = team;
        this.id = new UserFavoriteTeamId(
                member.getUserId(),
                team.getTeamId()
        );
    }

    public static UserFavoriteTeam create(Member member, Team team) {
        return new UserFavoriteTeam(member, team);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}