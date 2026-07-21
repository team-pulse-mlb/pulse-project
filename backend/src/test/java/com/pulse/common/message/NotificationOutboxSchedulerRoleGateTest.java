package com.pulse.common.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationOutboxSchedulerRoleGateTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationOutboxScheduler.class)
            .withBean(NotificationOutboxDispatcher.class, () -> mock(NotificationOutboxDispatcher.class));

    @Test
    @DisplayName("poller만 활성화되면 outbox 스케줄러를 등록한다")
    void shouldRegisterSchedulerWhenOnlyPollerIsEnabled() {
        contextRunner.withPropertyValues(
                        "pulse.poller.enabled=true",
                        "pulse.scorer.enabled=false")
                .run(context -> assertThat(context).hasSingleBean(NotificationOutboxScheduler.class));
    }

    @Test
    @DisplayName("scorer만 활성화되면 outbox 스케줄러를 등록한다")
    void shouldRegisterSchedulerWhenOnlyGameProcessorIsEnabled() {
        contextRunner.withPropertyValues(
                        "pulse.poller.enabled=false",
                        "pulse.scorer.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(NotificationOutboxScheduler.class));
    }

    @Test
    @DisplayName("poller와 scorer가 모두 비활성화되면 outbox 스케줄러를 등록하지 않는다")
    void shouldNotRegisterSchedulerWhenBothRolesAreDisabled() {
        contextRunner.withPropertyValues(
                        "pulse.poller.enabled=false",
                        "pulse.scorer.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(NotificationOutboxScheduler.class));
    }

    @Test
    @DisplayName("배치에서 스케줄러를 명시적으로 비활성화하면 scorer가 켜져 있어도 등록하지 않는다")
    void shouldNotRegisterSchedulerWhenExplicitlyDisabled() {
        contextRunner.withPropertyValues(
                        "pulse.poller.enabled=false",
                        "pulse.scorer.enabled=true",
                        "pulse.notification-outbox.scheduler-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(NotificationOutboxScheduler.class));
    }
}
