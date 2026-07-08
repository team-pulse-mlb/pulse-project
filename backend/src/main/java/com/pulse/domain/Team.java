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
 * balldontlie 팀 마스터. poller가 games FK를 만족시키기 위해 먼저 upsert한다.
 */
@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
public class Team {

    @Id
    @Column(name = "team_id")
    private Long id;

    private String abbreviation;

    private String displayName;

    private Instant createdAt;

    private Instant updatedAt;
}
