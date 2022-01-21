package com.czertainly.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;

import java.util.concurrent.Executor;

@Configuration
public class ExecutorConfig extends AsyncConfigurerSupport {
    @Override
    @Bean
    public Executor getAsyncExecutor() {
        return new ContextAwarePoolExecutor();
    }
}