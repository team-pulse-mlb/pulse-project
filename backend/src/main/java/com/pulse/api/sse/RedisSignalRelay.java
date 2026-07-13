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

    /**
     * 로그인 사용자에게 새 알림이 생성됐음을 알려주는 SSE 이벤트 이름입니다.
     */
    public static final String NOTIFICATION_CREATED_EVENT =
            "notification_created";

    private final SseEmitterRegistry emitterRegistry;
    private final ObjectMapper objectMapper;
    private final AtomicLong rankingSequence = new AtomicLong();
    private final AtomicLong gameSequence = new AtomicLong();

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel =
                new String(
                        message.getChannel(),
                        StandardCharsets.UTF_8
                );

        /*
         * notification 채널은 body에 notificationId를 담으므로
         * Redis 메시지 본문도 문자열로 변환합니다.
         */
        String body =
                new String(
                        message.getBody(),
                        StandardCharsets.UTF_8
                );

        try {
            if (RedisSignalChannels.RANKING.equals(channel)) {
                relayRankingChanged();
                return;
            }

            if (channel.startsWith(
                    RedisSignalChannels.GAME_PREFIX
            )) {
                relayGameUpdated(channel);
                return;
            }

            if (channel.startsWith(
                    RedisSignalChannels.NOTIFICATION_PREFIX
            )) {
                relayNotificationCreated(
                        channel,
                        body
                );
                return;
            }
        } catch (
                NumberFormatException
                | JsonProcessingException exception
        ) {
            /*
             * 잘못된 채널이나 payload는 현재 신호만 유실됩니다.
             *
             * 실제 알림은 DB에 저장되어 있으므로 사용자가 알림함을
             * 다시 조회하면 데이터를 복구할 수 있습니다.
             */
            log.warn(
                    "SSE 신호 중계 실패: channel={}, body={}",
                    channel,
                    body,
                    exception
            );
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

    /**
     * Redis 사용자별 알림 생성 신호를
     * 해당 사용자의 SSE 연결에만 전달합니다.
     *
     * Redis 채널:
     * signal:notification:{userId}
     *
     * Redis body:
     * notificationId
     *
     * SSE 이벤트:
     * notification_created
     *
     * SSE payload:
     * {
     *   "notificationId": 501
     * }
     */
    private void relayNotificationCreated(
            String channel,
            String body
    ) throws JsonProcessingException {

        /*
         * signal:notification:7에서
         * 접두사를 제외한 7을 userId로 사용합니다.
         */
        long userId = Long.parseLong(
                channel.substring(
                        RedisSignalChannels
                                .NOTIFICATION_PREFIX
                                .length()
                )
        );

        /*
         * NotificationSignalPublisher가 Redis body에
         * notificationId 문자열을 넣어 발행했습니다.
         */
        long notificationId =
                Long.parseLong(body.trim());

        NotificationCreatedPayload payload =
                new NotificationCreatedPayload(
                        notificationId
                );

        emitterRegistry.sendToUser(
                userId,
                NOTIFICATION_CREATED_EVENT,
                objectMapper.writeValueAsString(payload)
        );
    }

    private record RankingChangedPayload(long sequence, String generatedAt) {
    }

    private record GameUpdatedPayload(long gameId, long sequence, String generatedAt) {
    }

    /**
     * 새 알림 생성 SSE payload입니다.
     *
     * 클라이언트는 notificationId를 받은 뒤
     * GET /api/me/notifications를 다시 조회합니다.
     */
    private record NotificationCreatedPayload(
            long notificationId
    ) {
    }
}
