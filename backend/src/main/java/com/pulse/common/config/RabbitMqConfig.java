package com.pulse.common.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMqConfig {

    public static final String SCORE_TASKS_QUEUE = "score.tasks";
    public static final String SCORE_TASKS_DEAD_LETTER_QUEUE = SCORE_TASKS_QUEUE + ".dlq";
    public static final String NOTIFY_EVENTS_QUEUE = "notify.events";
    public static final String NOTIFY_EVENTS_DEAD_LETTER_QUEUE = NOTIFY_EVENTS_QUEUE + ".dlq";

    private static final String QUEUE_TYPE_ARGUMENT = "x-queue-type";
    private static final String QUEUE_TYPE_QUORUM = "quorum";
    private static final String DEAD_LETTER_EXCHANGE_ARGUMENT = "x-dead-letter-exchange";
    private static final String DEAD_LETTER_ROUTING_KEY_ARGUMENT = "x-dead-letter-routing-key";
    private static final String DELIVERY_LIMIT_ARGUMENT = "x-delivery-limit";
    private static final String DEFAULT_EXCHANGE = "";
    private static final int PREFETCH_COUNT = 5;
    private static final int DELIVERY_LIMIT_WITH_ONE_REDELIVERY = 2;

    @Bean
    public Queue scoreTasksQueue() {
        return workerQueue(SCORE_TASKS_QUEUE, SCORE_TASKS_DEAD_LETTER_QUEUE);
    }

    @Bean
    public Queue scoreTasksDeadLetterQueue() {
        return deadLetterQueue(SCORE_TASKS_DEAD_LETTER_QUEUE);
    }

    @Bean
    public Queue notifyEventsQueue() {
        return workerQueue(NOTIFY_EVENTS_QUEUE, NOTIFY_EVENTS_DEAD_LETTER_QUEUE);
    }

    @Bean
    public Queue notifyEventsDeadLetterQueue() {
        return deadLetterQueue(NOTIFY_EVENTS_DEAD_LETTER_QUEUE);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setPrefetchCount(PREFETCH_COUNT);
        factory.setDefaultRequeueRejected(true);
        return factory;
    }

    private Queue workerQueue(String queueName, String deadLetterQueueName) {
        return QueueBuilder.durable(queueName)
                .withArgument(QUEUE_TYPE_ARGUMENT, QUEUE_TYPE_QUORUM)
                .withArgument(DEAD_LETTER_EXCHANGE_ARGUMENT, DEFAULT_EXCHANGE)
                .withArgument(DEAD_LETTER_ROUTING_KEY_ARGUMENT, deadLetterQueueName)
                .withArgument(DELIVERY_LIMIT_ARGUMENT, DELIVERY_LIMIT_WITH_ONE_REDELIVERY)
                .build();
    }

    private Queue deadLetterQueue(String queueName) {
        return QueueBuilder.durable(queueName)
                .withArgument(QUEUE_TYPE_ARGUMENT, QUEUE_TYPE_QUORUM)
                .build();
    }
}
