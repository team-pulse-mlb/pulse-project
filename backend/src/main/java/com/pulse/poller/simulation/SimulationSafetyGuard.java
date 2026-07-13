package com.pulse.poller.simulation;

import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 설정 실수로 운영 프로필에서 시뮬레이션이 기동되는 것을 차단한다. */
@Component
@ConditionalOnProperty(prefix = "pulse.simulation", name = "enabled", havingValue = "true")
public class SimulationSafetyGuard implements ApplicationRunner {
    private final Environment environment;

    public SimulationSafetyGuard(Environment environment) { this.environment = environment; }

    @Override
    public void run(ApplicationArguments args) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            throw new IllegalStateException("simulation must not run with the prod profile");
        }
    }
}
