package com.pulse.api.notification.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * PostgreSQL의 INSERT ... RETURNING 문법을 사용해
 * 사용자 알림을 저장하고 생성된 PK를 반환합니다.
 *
 * JPA의 @Modifying 쿼리는 일반적으로 변경된 행 개수만 반환하므로,
 * 생성된 id가 필요한 이 작업은 Spring JDBC로 처리합니다.
 *
 * 현재 메서드는 NotificationFanOutService의 @Transactional 안에서 호출되므로
 * 기존 사용자 알림 저장 트랜잭션에 함께 참여합니다.
 */
@RequiredArgsConstructor
public class UserNotificationInsertRepositoryImpl
        implements UserNotificationInsertRepository {

    /**
     * 중복되지 않는 사용자 알림만 저장하고
     * 새로 생성된 user_notifications.id를 반환합니다.
     *
     * 중복인 경우 ON CONFLICT DO NOTHING 때문에 행이 생성되지 않고,
     * RETURNING 결과도 빈 목록이 됩니다.
     */
    private static final String INSERT_SQL = """
            INSERT INTO user_notifications (
                event_id,
                user_id,
                message,
                read_at,
                created_at
            )
            VALUES (
                :eventId,
                :userId,
                :message,
                NULL,
                :createdAt
            )
            ON CONFLICT (event_id, user_id) DO NOTHING
            RETURNING id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public OptionalLong insertIfAbsentReturningId(
            UUID eventId,
            Long userId,
            String message,
            Instant createdAt
    ) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("eventId", eventId)
                        .addValue("userId", userId)
                        .addValue("message", message)

                        /*
                         * PostgreSQL TIMESTAMPTZ 컬럼에 안전하게 전달되도록
                         * Instant를 JDBC Timestamp로 변환합니다.
                         */
                        .addValue(
                                "createdAt",
                                Timestamp.from(createdAt)
                        );

        /*
         * 새로운 행이 생성되면 id 한 건이 반환됩니다.
         * 중복이면 RETURNING 결과가 없으므로 빈 List가 반환됩니다.
         */
        List<Long> insertedIds = jdbcTemplate.query(
                INSERT_SQL,
                parameters,
                (resultSet, rowNumber) ->
                        resultSet.getLong("id")
        );

        if (insertedIds.isEmpty()) {
            return OptionalLong.empty();
        }

        return OptionalLong.of(
                insertedIds.get(0)
        );
    }
}