package com.pulse.scorer;

import com.pulse.common.metrics.PulseMetrics;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** AI HTTP 호출이 scorer 메시지 소비 스레드를 막지 않도록 전용 실행기를 구성한다. */
@Slf4j
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
        executor.setRejectedExecutionHandler(new AiGenerationRejectedExecutionHandler());
        return executor;
    }

    /** 부가 기능의 포화가 핵심 점수 처리 스레드로 전파되지 않도록 AI 생성 작업을 폐기한다. */
    static final class AiGenerationRejectedExecutionHandler implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
            PulseMetrics.increment("pulse.ai.generation.rejected");
            log.warn(
                    "AI 생성 실행기 포화로 작업을 폐기했습니다. 큐 크기={} 활성 스레드={}",
                    executor.getQueue().size(),
                    executor.getActiveCount());
        }
    }
}
