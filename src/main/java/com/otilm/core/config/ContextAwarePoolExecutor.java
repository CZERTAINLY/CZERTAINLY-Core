package com.otilm.core.config;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextCallable;
import org.springframework.web.context.request.RequestContextHolder;

public class ContextAwarePoolExecutor extends ThreadPoolTaskExecutor {
    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return super.submit(new DelegatingSecurityContextCallable(
                new ContextAwareCallable(task, RequestContextHolder.currentRequestAttributes())));
    }
}
