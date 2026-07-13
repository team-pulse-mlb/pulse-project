package com.pulse.api.sse;

import com.pulse.common.message.RedisSignalChannels;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** 재조회 신호 채널 구독 컨테이너 구성. api 역할에서만 활성화된다. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "pulse.sse", name = "enabled", havingValue = "true")
public class SseRelayConfig {

    @Bean
    public RedisMessageListenerContainer sseSignalListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            RedisSignalRelay redisSignalRelay
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(redisSignalRelay, List.of(
                new ChannelTopic(RedisSignalChannels.RANKING),
                new PatternTopic(RedisSignalChannels.GAME_PATTERN)
        ));
        return container;
    }
}
