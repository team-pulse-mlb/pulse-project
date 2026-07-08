package com.pulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 선수 마스터. 라인업·시즌 스탯이 참조하는 최소 컬럼만 매핑한다.
 */
@Entity
@Table(name = "players")
@Getter
@Setter
@NoArgsConstructor
public class Player {

    /** balldontlie 선수 id를 그대로 PK로 사용한다. */
    @Id
    @Column(name = "player_id")
    private Long id;

    private String fullName;

    private String firstName;

    private String lastName;

    private String position;

    @Column(name = "team_id")
    private Long teamId;

    private Instant createdAt;

    private Instant updatedAt;
}
