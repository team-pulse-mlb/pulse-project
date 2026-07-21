package com.pulse.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 알림 이벤트 Consumer가 설정값에 따라
 * API 역할에서만 등록되는지 검증하는 테스트입니다.
 *
 * 운영 구성:
 * - api: pulse.notification.consumer-enabled=true
 * - poller: false
 * - scorer: false
 *
 * 이 테스트는 RabbitMQ 메시지 처리 내용이 아니라
 * NotificationEventListener Bean의 생성 여부만 검사합니다.
 */
class NotificationConsumerRoleGateTest {

    /**
     * 실제 NotificationFanOutService 대신 Mock Bean을 등록합니다.
     *
     * 이 테스트의 목적은 fan-out 로직 검증이 아니라
     * NotificationEventListener의 조건부 생성 여부 확인이기 때문입니다.
     */
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(NotificationEventListener.class)
                    .withBean(
                            NotificationFanOutService.class,
                            () -> mock(NotificationFanOutService.class)
                    );

    /**
     * API 컨테이너처럼 consumer-enabled=true인 경우
     * 알림 Listener가 생성되어야 합니다.
     */
    @Test
    @DisplayName("consumer-enabled=true면 알림 이벤트 Listener를 등록한다")
    void shouldRegisterListenerWhenConsumerIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "pulse.notification.consumer-enabled=true"
                )
                .run(context ->
                        assertThat(context)
                                .hasSingleBean(NotificationEventListener.class)
                );
    }

    /**
     * poller나 scorer처럼 consumer-enabled=false인 경우
     * notify.events 큐를 소비하면 안 됩니다.
     */
    @Test
    @DisplayName("consumer-enabled=false면 알림 이벤트 Listener를 등록하지 않는다")
    void shouldNotRegisterListenerWhenConsumerIsDisabled() {
        contextRunner
                .withPropertyValues(
                        "pulse.notification.consumer-enabled=false"
                )
                .run(context ->
                        assertThat(context)
                                .doesNotHaveBean(NotificationEventListener.class)
                );
    }

    /**
     * 설정값을 빠뜨린 서버도 안전하게 Consumer가 꺼져야 합니다.
     *
     * @ConditionalOnProperty에 matchIfMissing=true를 사용하지 않았으므로
     * 설정이 없을 때 Listener는 생성되지 않습니다.
     */
    @Test
    @DisplayName("consumer-enabled 설정이 없으면 알림 이벤트 Listener를 등록하지 않는다")
    void shouldNotRegisterListenerWhenPropertyIsMissing() {
        contextRunner
                .run(context ->
                        assertThat(context)
                                .doesNotHaveBean(NotificationEventListener.class)
                );
    }
}