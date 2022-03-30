package com.czertainly.core;

import com.czertainly.core.config.ContextAwarePoolExecutor;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import javax.annotation.Nonnull;
import java.util.Map;

@SpringBootApplication
@EnableAsync
public class Application extends SpringBootServletInitializer {

	//Number of async pools in action at a time
	private static final Integer POOL_SIZE = 10;
	//Maximum queue size for the async operations when no pool is available to take action
	private static final Integer QUEUE_SIZE = 500;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Application.class);
    }

	static class ContextCopyingDecorator implements TaskDecorator {
		@Nonnull
		@Override
		public Runnable decorate(@Nonnull Runnable runnable) {
			RequestAttributes context =
					RequestContextHolder.currentRequestAttributes();
			Map<String, String> contextMap = MDC.getCopyOfContextMap();
			return () -> {
				try {
					RequestContextHolder.setRequestAttributes(context);
					MDC.setContextMap(contextMap);
					runnable.run();
				} finally {
					MDC.clear();
					RequestContextHolder.resetRequestAttributes();
				}
			};
		}
	}

    @Bean("threadPoolTaskExecutor")
	public TaskExecutor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ContextAwarePoolExecutor();
		executor.setCorePoolSize(POOL_SIZE);
		executor.setMaxPoolSize(POOL_SIZE);
		executor.setQueueCapacity(QUEUE_SIZE);
		executor.setThreadNamePrefix("RAProfileCore-");
		executor.initialize();
		return new DelegatingSecurityContextAsyncTaskExecutor(executor);
	}
}