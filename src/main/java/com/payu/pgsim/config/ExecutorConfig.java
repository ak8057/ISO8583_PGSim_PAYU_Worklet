package com.payu.pgsim.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {

    private ExecutorService executor;

    @Bean
    public ExecutorService isoProcessingExecutor() {
        int threads = Math.max(4, Runtime.getRuntime().availableProcessors());
        executor = Executors.newFixedThreadPool(threads);
        return executor;
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}