package com.pulse.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.test.util.ReflectionTestUtils;

class RabbitMqConfigTest {

    private final RabbitMqConfig config = new RabbitMqConfig();

    @Test
    void scoreTasksQueue_shouldDeclareDeadLetterAndDeliveryLimit() {
        Queue queue = config.scoreTasksQueue();

        assertThat(queue.getName()).isEqualTo("score.tasks");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-queue-type", "quorum")
                .containsEntry("x-dead-letter-exchange", "")
                .containsEntry("x-dead-letter-routing-key", "score.tasks.dlq")
                .containsEntry("x-delivery-limit", 2);
    }

    @Test
    void notifyEventsQueue_shouldDeclareDeadLetterAndDeliveryLimit() {
        Queue queue = config.notifyEventsQueue();

        assertThat(queue.getName()).isEqualTo("notify.events");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-queue-type", "quorum")
                .containsEntry("x-dead-letter-exchange", "")
                .containsEntry("x-dead-letter-routing-key", "notify.events.dlq")
                .containsEntry("x-delivery-limit", 2);
    }

    @Test
    void deadLetterQueues_shouldDeclareDurableQuorumQueues() {
        Queue scoreDeadLetterQueue = config.scoreTasksDeadLetterQueue();
        Queue notifyDeadLetterQueue = config.notifyEventsDeadLetterQueue();

        assertThat(scoreDeadLetterQueue.getName()).isEqualTo("score.tasks.dlq");
        assertThat(scoreDeadLetterQueue.isDurable()).isTrue();
        assertThat(scoreDeadLetterQueue.getArguments()).containsEntry("x-queue-type", "quorum");

        assertThat(notifyDeadLetterQueue.getName()).isEqualTo("notify.events.dlq");
        assertThat(notifyDeadLetterQueue.isDurable()).isTrue();
        assertThat(notifyDeadLetterQueue.getArguments()).containsEntry("x-queue-type", "quorum");
    }

    @Test
    void rabbitListenerContainerFactory_shouldSetPrefetchAndRequeueRejected() {
        SimpleRabbitListenerContainerFactory factory = config.rabbitListenerContainerFactory(
                new SimpleRabbitListenerContainerFactoryConfigurer(new RabbitProperties()),
                mock(ConnectionFactory.class)
        );

        assertThat(ReflectionTestUtils.getField(factory, "prefetchCount")).isEqualTo(5);
        assertThat(ReflectionTestUtils.getField(factory, "defaultRequeueRejected")).isEqualTo(Boolean.TRUE);
    }
}
