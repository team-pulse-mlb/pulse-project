package com.pulse.api.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * user_favorite_players 테이블의 복합 기본 키입니다.
 *
 * 한 사용자가 같은 선수를 중복 등록하지 못하도록
 * user_id와 player_id를 하나의 기본 키로 사용합니다.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserFavoritePlayerId implements Serializable {

    /**
     * 관심 선수를 등록한 사용자의 PK입니다.
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * 사용자가 관심 선수로 등록한 선수의 PK입니다.
     *
     * players.player_id를 참조합니다.
     */
    @Column(name = "player_id")
    private Long playerId;
}