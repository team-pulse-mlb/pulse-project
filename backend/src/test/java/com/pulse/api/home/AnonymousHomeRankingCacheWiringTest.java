package com.pulse.api.home;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * AnonymousHomeRankingCache는 생성자가 둘 이상이라 주입 생성자를 명시하지 않으면
 * Spring이 기본 생성자를 찾다 실패해 애플리케이션 컨텍스트 자체가 뜨지 못한다.
 * 이 테스트는 컴포넌트가 실제 컨텍스트에서 정상 생성되는지 검증해 부팅 실패 재발을 막는다.
 */
class AnonymousHomeRankingCacheWiringTest {

    @Test
    void 스프링_컨텍스트에서_정상적으로_생성된다() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            context.registerBean(
                    HomeRankingCacheProperties.class,
                    () -> new HomeRankingCacheProperties(Duration.ofSeconds(3))
            );
            context.register(AnonymousHomeRankingCache.class);
            context.refresh();

            assertThat(context.getBean(AnonymousHomeRankingCache.class)).isNotNull();
        }
    }
}
