package jh.com.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 解析专用线程池。禁用默认的 SimpleAsyncTaskExecutor——它每次新建线程且无上限。
 */
@Configuration
public class ParseExecutorConfig {

    @Value("${parse.executor.core-pool-size}")
    private int corePoolSize;

    @Value("${parse.executor.max-pool-size}")
    private int maxPoolSize;

    @Value("${parse.executor.queue-capacity}")
    private int queueCapacity;

    @Bean("parseExecutor")
    public ThreadPoolTaskExecutor quizParseExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(corePoolSize);
        ex.setMaxPoolSize(maxPoolSize);
        ex.setQueueCapacity(queueCapacity);          // 有界队列，防止无限堆积
        ex.setThreadNamePrefix("quiz-parse-");
        // 拒绝即抛 RejectedExecutionException，上层翻译成「系统繁忙」
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(60);
        ex.initialize();
        return ex;
    }
}
