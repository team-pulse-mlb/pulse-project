package com.pulse.api.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.message.RedisSignalChannels;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis pub/sub 재조회 신호를 SSE 이벤트로 변환하는 중계자.
 * payload에는 점수·결과를 싣지 않고, 클라이언트가 REST를 재조회할 근거만 담는다
 * (API_CONTRACTS.md §2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "pulse.sse", name = "enabled", havingValue = "true")
public class RedisSignalRelay implements MessageListener {

    public static final String RANKING_CHANGED_EVENT = "ranking_changed";
    public static final String GAME_UPDATED_EVENT = "game_updated";

    private final SseEmitterRegistry emitterRegistry;
    private final ObjectMapper objectMapper;
    private final AtomicLong rankingSequence = new AtomicLong();
    private final AtomicLong gameSequence = new AtomicLong();

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        try {
            if (RedisSignalChannels.RANKING.equals(channel)) {
                relayRankingChanged();
                return;
            }
            if (channel.startsWith(RedisSignalChannels.GAME_PREFIX)) {
                relayGameUpdated(channel);
            }
        } catch (NumberFormatException | JsonProcessingException e) {
            // 신호 유실은 다음 신호나 클라이언트 재조회로 자연 복구되므로 기록만 남긴다.
            log.warn("SSE 신호 중계 실패: channel={}", channel, e);
        }
    }

    private void relayRankingChanged() throws JsonProcessingException {
        RankingChangedPayload payload = new RankingChangedPayload(
                rankingSequence.incrementAndGet(),
                Instant.now().toString()
        );
        emitterRegistry.broadcast(RANKING_CHANGED_EVENT, objectMapper.writeValueAsString(payload));
    }

    private void relayGameUpdated(String channel) throws JsonProcessingException {
        long gameId = Long.parseLong(channel.substring(RedisSignalChannels.GAME_PREFIX.length()));
        GameUpdatedPayload payload = new GameUpdatedPayload(
                gameId,
                gameSequence.incrementAndGet(),
                Instant.now().toString()
        );
        emitterRegistry.broadcast(GAME_UPDATED_EVENT, objectMapper.writeValueAsString(payload));
    }

    private record RankingChangedPayload(long sequence, String generatedAt) {
    }

    private record GameUpdatedPayload(long gameId, long sequence, String generatedAt) {
    }
}
