package org.interviewer.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.interviewer.entity.ollama.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Aspect 1 of 3 — every model call, timed and token-counted.
 *
 * <p>An aspect rather than instrumentation inside {@code OllamaHttpClient} for one reason: it also
 * covers the <b>grader</b>, which does not go through {@code AgentEvents} and would otherwise be
 * invisible. Grading is ~82 s per session — by far the most expensive single operation in the
 * system — and it was, until this aspect, entirely unmeasured.
 *
 * <p>The token counters are the C1 accounting, and the ratio is the point. On the demo run the
 * interviewer spent 78,825 prompt tokens against 2,279 generated: <b>35 to 1</b>. Prompt evaluation
 * runs at 78 tok/s cold against 9-10 tok/s generating, so almost all wall-clock time is spent
 * re-reading the conversation rather than producing anything. Any latency work that does not start
 * from that ratio is optimising the wrong term.
 */
@Slf4j
@Aspect
@Component
public class LlmCallAspect {

    private final MeterRegistry registry;

    public LlmCallAspect(MeterRegistry registry) {
        this.registry = registry;
    }

    @Around("execution(* org.interviewer.llm.OllamaClient.chat(..))")
    public Object timeChat(ProceedingJoinPoint joinPoint) throws Throwable {
        long started = System.nanoTime();
        String outcome = "ok";
        try {
            Object result = joinPoint.proceed();
            if (result instanceof ChatResponse response) {
                recordTokens(response);
            }
            return result;
        } catch (Throwable t) {
            // Distinguished from "ok" because an unreachable model is rung 9 - a different event
            // from a model that answered badly - and the two should not share a timer.
            outcome = t.getClass().getSimpleName();
            throw t;
        } finally {
            long ms = (System.nanoTime() - started) / 1_000_000L;
            Timer.builder("interview.llm.call")
                    .tag("outcome", outcome)
                    .register(registry)
                    .record(ms, TimeUnit.MILLISECONDS);
            if (ms > 15_000) {
                // Worth a log line: on this hardware that is a cold prompt, which means the prefix
                // cache missed and something upstream changed the immutable part of the prompt.
                log.info("slow model call: {} ms (cold prompt or cache miss)", ms);
            }
        }
    }

    private void recordTokens(ChatResponse response) {
        if (response.getPromptEvalCount() != null) {
            registry.summary("interview.llm.prompt_tokens").record(response.getPromptEvalCount());
        }
        if (response.getEvalCount() != null) {
            registry.summary("interview.llm.completion_tokens").record(response.getEvalCount());
        }
    }
}
