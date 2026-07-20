package com.pulse.common.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.NotificationEventLogRepository;
import com.pulse.domain.NotificationOutboxRepository;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 알림 발행 빈들이 실제 빈 정의 경로(생성자 자동 주입)로 생성되는지 검증한다.
 * 게이트 테스트처럼 mock 공급자로 등록하면 생성자 해석을 우회하므로,
 * Clock 주입용 생성자가 늘어날 때 @Autowired 누락을 잡지 못한다.
 */
class NotificationMessageBeanConstructionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationEventPublisher.class, NotificationOutboxDispatcher.class)
            .withBean(NotificationEventLogRepository.class, () -> mock(NotificationEventLogRepository.class))
            .withBean(NotificationOutboxRepository.class, () -> mock(NotificationOutboxRepository.class))
            .withBean(AfterCommitExecutor.class, () -> mock(AfterCommitExecutor.class))
            .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
            .withBean(NotificationOutboxProperties.class,
                    () -> new NotificationOutboxProperties(
                            Duration.ofSeconds(5),
                            Duration.ofMinutes(5),
                            50,
                            Duration.ofSeconds(1),
                            Duration.ofDays(7),
                            500
                    ));

    @Test
    @DisplayName("발행자와 디스패처를 생성자 자동 주입으로 생성한다")
    void shouldInstantiatePublisherAndDispatcherByConstructorInjection() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(NotificationEventPublisher.class);
            assertThat(context).hasSingleBean(NotificationOutboxDispatcher.class);
        });
    }
}
