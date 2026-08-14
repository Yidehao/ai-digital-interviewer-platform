package org.interviewer.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.interviewer.utils.MdcKeys;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Aspect 2 of 3 — every tool dispatch, from wherever it came.
 *
 * <p>This is not a duplicate of the {@code AgentEvents} tool metrics, and the difference is the
 * reason it exists. {@code AgentEvents} only sees calls made by the agent loop. <b>MCP tool calls
 * bypass it entirely</b> — an external client can invoke {@code run_code} through
 * {@code /mcp/message} and, before this aspect, nothing recorded that it happened. For the one
 * component that executes untrusted code, "we do not log who called it" is not an acceptable
 * position.
 *
 * <p>{@code interview.tool.dispatch} is therefore the count of <em>all</em> invocations, while
 * {@code interview.tool.duration} from the loop remains the loop's own view including its timeout
 * enforcement. A gap between the two is MCP traffic, which is exactly the thing worth being able
 * to see.
 */
@Slf4j
@Aspect
@Component
public class ToolDispatchAspect {

    private final MeterRegistry registry;

    public ToolDispatchAspect(MeterRegistry registry) {
        this.registry = registry;
    }

    @Around("execution(* org.interviewer.agent.gateway.ToolGateway.invoke(..))")
    public Object timeDispatch(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String toolName = args.length > 0 && args[0] != null ? args[0].toString() : "unknown";

        org.slf4j.MDC.put(MdcKeys.TOOL, toolName);
        long started = System.nanoTime();
        String outcome = "ok";
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            outcome = "error";
            throw t;
        } finally {
            Timer.builder("interview.tool.dispatch")
                    .tag("tool", toolName)
                    .tag("outcome", outcome)
                    .register(registry)
                    .record((System.nanoTime() - started) / 1_000_000L, TimeUnit.MILLISECONDS);
            org.slf4j.MDC.remove(MdcKeys.TOOL);
        }
    }
}
