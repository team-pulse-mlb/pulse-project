package com.pulse.scorer;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** AI HTTP 호출이 scorer 메시지 소비 스레드를 막지 않도록 전용 실행기를 구성한다. */
@EnableAsync
@Configuration(proxyBeanMethods = false)
class AiGenerationAsyncConfig {

    static final String TASK_EXECUTOR = "aiGenerationTaskExecutor";

    @Bean(name = TASK_EXECUTOR)
    ThreadPoolTaskExecutor aiGenerationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-generation-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}
