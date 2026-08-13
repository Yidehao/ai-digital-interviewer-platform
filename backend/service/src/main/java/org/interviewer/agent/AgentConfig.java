package org.interviewer.agent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Beans the loop needs that are not components in their own right.
 */
@Configuration
public class AgentConfig {

    /**
     * Runs tool dispatches so the loop can bound them.
     *
     * <p>The timeout has to be enforced by the caller, which means the call has to happen somewhere
     * the caller can abandon. A tool that timed itself out would still leave the loop blocked on a
     * tool that decided not to return — which is precisely the hang this design exists to prevent.
     *
     * <p>Unbounded and caching rather than fixed-size: these threads are almost always idle, and a
     * fixed pool that fills would queue tool calls behind each other and turn one slow tool into
     * every session's slow tool.
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "toolExecutor")
    public ExecutorService toolExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                0, 64, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
                runnable -> {
                    Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                    thread.setName("agent-tool-" + thread.getId());
                    thread.setDaemon(true);
                    return thread;
                });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * Injectable so wall-clock budget exhaustion is testable without a 45-minute test.
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock agentClock() {
        return Clock.systemUTC();
    }
}
