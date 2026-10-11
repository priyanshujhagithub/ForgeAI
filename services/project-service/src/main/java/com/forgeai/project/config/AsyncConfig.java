package com.forgeai.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    // Bounded on purpose. Each run drives a model and uses sandbox credit, so
    // runs are worked through a few at a time and the rest wait in a short
    // queue. When the queue is full, starting another run is rejected.
    @Bean
    public ThreadPoolTaskExecutor agentRunTaskExecutor(
            @Value("${agent-runs.max-concurrent}") int maxConcurrent,
            @Value("${agent-runs.queue-capacity}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(maxConcurrent);
        executor.setMaxPoolSize(maxConcurrent);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("agent-run-");
        return executor;
    }
}
