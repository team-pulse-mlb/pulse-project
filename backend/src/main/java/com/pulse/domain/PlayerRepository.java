package com.pulse.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    /** 이름이 채워지지 않은 스텁 선수 조회. 보강 배치의 런당 상한 제한용으로 Pageable을 받는다. */
    List<Player> findByFullNameIsNull(Pageable pageable);


    /**
     * 관심 선수 설정 화면에서 선수 이름을 검색합니다.
     *
     * 검색 정책:
     * - 이름이 없는 스텁 선수 제외
     * - 영문 이름 일부 검색
     * - 대소문자 구분 없음
     * - 선수 이름 오름차순
     * - 반환 개수는 호출 측의 Pageable로 제한
     */
    List<Player>
    findByFullNameIsNotNullAndFullNameContainingIgnoreCaseOrderByFullNameAsc(
            String keyword,
            Pageable pageable
    );


    /**
     * 선수 한 명을 PostgreSQL의 원자적 upsert로 저장합니다.
     *
     * 기존의 SELECT → INSERT 방식과 달리,
     * 여러 트랜잭션이 같은 player_id를 동시에 저장해도
     * PostgreSQL이 PK 충돌을 직접 처리합니다.
     *
     * 갱신 정책:
     * - null 또는 공백 문자열은 기존 값을 유지
     * - replaceTeam=true이면 teamId가 null이어도 그대로 반영
     * - replaceTeam=false이면 기존 team_id 유지
     * - created_at은 기존 행 충돌 시 유지
     * - updated_at은 새 관측 시각으로 갱신
     */
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query(
            value = """
                INSERT INTO players (
                    player_id,
                    full_name,
                    first_name,
                    last_name,
                    position,
                    team_id,
                    created_at,
                    updated_at
                )
                VALUES (
                    :playerId,
                    :fullName,
                    :firstName,
                    :lastName,
                    :position,
                    :teamId,
                    :observedAt,
                    :observedAt
                )
                ON CONFLICT (player_id)
                DO UPDATE SET
                    full_name = COALESCE(
                        NULLIF(BTRIM(EXCLUDED.full_name), ''),
                        players.full_name
                    ),
                    first_name = COALESCE(
                        NULLIF(BTRIM(EXCLUDED.first_name), ''),
                        players.first_name
                    ),
                    last_name = COALESCE(
                        NULLIF(BTRIM(EXCLUDED.last_name), ''),
                        players.last_name
                    ),
                    position = COALESCE(
                        NULLIF(BTRIM(EXCLUDED.position), ''),
                        players.position
                    ),
                    team_id = CASE
                        WHEN :replaceTeam
                            THEN EXCLUDED.team_id
                        ELSE players.team_id
                    END,
                    updated_at = EXCLUDED.updated_at
                """,
            nativeQuery = true
    )
    int upsertPlayer(
            @Param("playerId")
            Long playerId,

            @Param("fullName")
            String fullName,

            @Param("firstName")
            String firstName,

            @Param("lastName")
            String lastName,

            @Param("position")
            String position,

            @Param("teamId")
            Long teamId,

            @Param("replaceTeam")
            boolean replaceTeam,

            @Param("observedAt")
            Instant observedAt
    );
}
