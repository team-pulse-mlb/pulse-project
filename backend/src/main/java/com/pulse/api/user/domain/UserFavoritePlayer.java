package com.pulse.api.user.domain;

import com.pulse.domain.Player;
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

import java.time.Instant;

/**
 * 사용자가 등록한 관심 선수 정보입니다.
 *
 * users와 players 사이의 다대다 관계를
 * user_favorite_players 조인 테이블로 표현합니다.
 */
@Entity
@Table(name = "user_favorite_players")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFavoritePlayer {

    /**
     * user_id와 player_id로 구성된 복합 기본 키입니다.
     */
    @EmbeddedId
    private UserFavoritePlayerId id;

    /**
     * 관심 선수를 등록한 사용자입니다.
     *
     * 실제 외래 키 컬럼은 user_id이며,
     * 지연 로딩으로 불필요한 회원 조회를 줄입니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private Member member;

    /**
     * 사용자가 관심 선수로 선택한 선수입니다.
     *
     * 실제 외래 키 컬럼은 player_id이며,
     * players 테이블의 Player 엔티티와 연결됩니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("playerId")
    @JoinColumn(name = "player_id")
    private Player player;

    /**
     * 해당 선수를 관심 선수로 등록한 시각입니다.
     *
     * DB 컬럼이 TIMESTAMPTZ이므로 Instant를 사용합니다.
     */
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    private UserFavoritePlayer(
            Member member,
            Player player
    ) {
        this.member = member;
        this.player = player;

        this.id = new UserFavoritePlayerId(
                member.getUserId(),
                player.getId()
        );
    }

    /**
     * 관심 선수 엔티티 생성 메서드입니다.
     */
    public static UserFavoritePlayer create(
            Member member,
            Player player
    ) {
        return new UserFavoritePlayer(member, player);
    }

    /**
     * INSERT 직전에 등록 시각을 자동으로 기록합니다.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}