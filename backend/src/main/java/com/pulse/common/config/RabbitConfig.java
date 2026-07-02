package com.pulse.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * poller → scorer 메시지 큐 구성. 처리 실패 메시지는 DLQ에 보관한다.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "pulse.exchange";
    public static final String SCORE_QUEUE = "score.calculate.q";
    public static final String SCORE_ROUTING_KEY = "score.calculate";
    public static final String SCORE_DLQ = "score.calculate.dlq";

    @Bean
    public DirectExchange pulseExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Queue scoreQueue() {
        return QueueBuilder.durable(SCORE_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", SCORE_DLQ)
                .build();
    }

    @Bean
    public Queue scoreDlq() {
        return QueueBuilder.durable(SCORE_DLQ).build();
    }

    @Bean
    public Binding scoreBinding() {
        return BindingBuilder.bind(scoreQueue()).to(pulseExchange()).with(SCORE_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
