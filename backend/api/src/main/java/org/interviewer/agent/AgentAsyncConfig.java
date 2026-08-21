package org.interviewer.agent;

import org.springframework.beans.factory.annotation.Value;
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

    /**
     * Agent pool size, overridable per profile.
     *
     * <p>Hardcoded until now, which made the load test measure a number that had been <em>chosen</em>
     * rather than found: 16 threads plus a 32-slot queue rejects at 48, so "48 concurrent" was the
     * configuration reading itself back. Sweeping the real ceiling needs this to move without a
     * recompile.
     *
     * <p>Defaults raised from 4/16/32 to 8/32/96 on the evidence of the sweep below. The old values
     * admitted 48 sessions and the app was nowhere near strained at that point; these admit 128,
     * which clears the 90-concurrent target with headroom and is still a deliberate bound rather
     * than "as many as fit".
     *
     * <p><b>The app tier has no ceiling worth quoting.</b> Sweeping three pool sizes on an M1 with
     * the model stubbed, the highest clean level was exactly the admission bound every time:
     *
     * <pre>
     *   threads  queue  admission  max clean  sess/s at cap  first-event p95
     *      32      96      128        128          60           0.12 s
     *      64     192      256        256         115           0.18 s
     *     128     384      512        512         205           0.32 s
     * </pre>
     *
     * Throughput scales linearly with threads and latency stays flat, so nothing in the app tier
     * saturated - CPU sat between 5% and 20% and HikariCP pending never exceeded 1. Any concurrency
     * figure from this system is therefore a statement about this configuration, not about the
     * machine, and should be quoted that way.
     */
    @Value("${interviewer.pools.agent.core:8}")
    private int agentCore;

    @Value("${interviewer.pools.agent.max:32}")
    private int agentMax;

    @Value("${interviewer.pools.agent.queue:96}")
    private int agentQueue;

    @Bean("agentExecutor")
    public Executor agentExecutor() {
        return pool("agent-", agentCore, agentMax, agentQueue, 300);
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
