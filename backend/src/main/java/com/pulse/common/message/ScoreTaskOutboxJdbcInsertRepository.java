package com.pulse.common.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.pulse.domain.ScoreTaskOutbox;
import com.pulse.domain.ScoreTaskOutboxInsertRepository;
import java.sql.Timestamp;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ScoreTaskOutboxJdbcInsertRepository implements ScoreTaskOutboxInsertRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ScoreTaskOutboxJdbcInsertRepository(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapperProvider.getIfAvailable(
                () -> JsonMapper.builder().findAndAddModules().build()
        );
    }

    @Override
    public boolean insertPending(ScoreTaskOutbox outbox) {
        return jdbcTemplate.update("""
                INSERT INTO score_task_outbox (
                    outbox_id, game_id, observed_at, payload, status,
                    attempt_count, next_attempt_at, created_at
                ) VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
                ON CONFLICT (game_id, observed_at) DO NOTHING
                """,
                outbox.getOutboxId(),
                outbox.getGameId(),
                Timestamp.from(outbox.getObservedAt()),
                serializePayload(outbox),
                outbox.getStatus(),
                outbox.getAttemptCount(),
                Timestamp.from(outbox.getNextAttemptAt()),
                Timestamp.from(outbox.getCreatedAt())
        ) == 1;
    }

    private String serializePayload(ScoreTaskOutbox outbox) {
        try {
            return objectMapper.writeValueAsString(outbox.getPayload());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("ScoreTask payload를 JSON으로 변환할 수 없습니다.", exception);
        }
    }
}
