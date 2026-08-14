package org.interviewer.agent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Executors for the agent, and the point at which {@code @Async} starts working.
 *
 * <p><b>Read this before assuming it is boilerplate.</b> {@code OllamaTask} has carried an
 * {@code @Async} annotation since long before this migration, and no {@code @EnableAsync} has ever
 * existed — so the annotation has always been inert and legacy grading has always run
 * synchronously on the request thread. Adding {@code @EnableAsync} here makes that annotation take
 * effect for the first time, which would silently convert the legacy path from blocking to
 * fire-and-forget: {@code /interviewRecord/collect} would return before grading finished, and the
 * client would read an empty result.
 *
 * <p>The annotation is therefore removed from {@code OllamaTask} in the same commit. A feature
 * should not change unrelated behaviour as a side effect, and "we enabled async and the old grading
 * endpoint started returning early" is a bug that would take a long time to attribute correctly.
 *
 * <p>Three pools, because they fail differently:
 *
 * <ul>
 *   <li><b>agentExecutor</b> — one interview per thread, long-lived. Bounded queue: if the queue is
 *       full the system is oversubscribed, and rejecting a new interview is honest where queueing
 *       it behind twenty others is not.</li>
 *   <li><b>llmExecutor</b> — model calls. Deliberately small: Ollama serialises on one model
 *       anyway, so a large pool would only queue requests inside Ollama where there are no metrics
 *       and no timeouts we control.</li>
 *   <li><b>gradingExecutor</b> — post-interview grading. ~82 s per session, so this must never
 *       share a pool with anything a candidate is waiting on.</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AgentAsyncConfig {

    @Bean("agentExecutor")
    public Executor agentExecutor() {
        return pool("agent-", 4, 16, 32, 300);
    }

    @Bean("llmExecutor")
    public Executor llmExecutor() {
        // Small on purpose. Ollama processes one request per loaded model at a time; a bigger pool
        // moves the queue somewhere we cannot see it.
        return pool("llm-", 2, 4, 64, 120);
    }

    @Bean("gradingExecutor")
    public Executor gradingExecutor() {
        return pool("grading-", 1, 2, 100, 600);
    }

    private ThreadPoolTaskExecutor pool(String prefix, int core, int max, int queue, int keepAlive) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setKeepAliveSeconds(keepAlive);
        executor.setThreadNamePrefix(prefix);
        // CallerRunsPolicy would silently run an interview on a Tomcat thread and make the app look
        // healthy while its request threads drained. Rejecting is louder and truer.
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
