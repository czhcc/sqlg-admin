package com.trs.modules.task.engine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 任务执行线程池配置。
 *
 * @author czh
 * @date 2026/07/23
 */
@Configuration
public class TaskExecutorConfig {

    @Autowired
    private Environment env;

    @Bean(name = "taskExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(env.getProperty("app.task.core-pool-size", Integer.class, 4));
        executor.setMaxPoolSize(env.getProperty("app.task.max-pool-size", Integer.class, 8));
        executor.setQueueCapacity(env.getProperty("app.task.queue-capacity", Integer.class, 100));
        executor.setThreadNamePrefix("task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
