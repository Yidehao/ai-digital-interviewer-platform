package org.interviewer.agent.stream;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.interviewer.entity.agent.FallbackReason;
import org.interviewer.entity.agent.InterviewSession;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * What to watch in production, and why each one is here.
 *
 * <p>Metrics chosen because they can <em>disconfirm</em> a claim, not because they are easy to
 * collect:
 *
 * <ul>
 *   <li><b>{@code interview_fallback_total{reason}}</b> — the one that matters most. Every rung of
 *       the ladder firing is the system working, but a sustained rate above roughly 5% means the
 *       model is not actually driving the interview and the "model-driven" framing needs softening.
 *       This is deliberately instrumented in Phase 6 rather than at the end: it is much better to
 *       learn it now than after three more phases are built on top of it.</li>
 *   <li><b>{@code interview_tool_schema_rejected_total{tool,side}}</b> — {@code side=args} should
 *       sit near zero with the current schemas; if it rises, a schema has drifted from how the
 *       model actually talks. {@code side=result} should be flat zero, because that side is our own
 *       output — any movement there is a bug we shipped.</li>
 *   <li><b>{@code interview_prompt_tokens}</b> — prompt evaluation dominates first-token latency
 *       (78 tok/s cold against 9-10 tok/s generating), so prompt-token growth across a session is
 *       the measurement that proves or disproves that the sliding window is doing its job. A
 *       latency number without this is a number measured at turn 2 and quoted for turn 20.</li>
 *   <li><b>{@code interview_tool_duration}</b> — per tool, so {@code run_code}'s container start
 *       does not hide inside an average with four tools that only touch memory.</li>
 * </ul>
 */
@Component
public class AgentMetrics {

    private final MeterRegistry registry;

    public AgentMetrics(MeterRegistry registry, EmitterRegistry emitters) {
        this.registry = registry;
        // A gauge rather than a counter: the question is "how many candidates are connected right
        // now", which a counter cannot answer.
        registry.gauge("interview.stream.live", emitters, EmitterRegistry::liveCount);
    }

    public void toolStarted(String toolName) {
        registry.counter("interview.tool.calls", "tool", toolName).increment();
    }

    public void toolFinished(String toolName, long durationMs, boolean ok) {
        Timer.builder("interview.tool.duration")
                .tag("tool", toolName)
                .tag("outcome", ok ? "ok" : "error")
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /** Rung fired. The label is the rung, so a rising rate can be attributed rather than guessed. */
    public void fallback(FallbackReason reason) {
        registry.counter("interview.fallback", "reason", reason.name()).increment();
    }

    public void schemaRejected(String toolName, String side) {
        registry.counter("interview.tool.schema_rejected", "tool", toolName, "side", side)
                .increment();
    }

    /**
     * Recorded once per finished session rather than per turn.
     *
     * <p>Per-turn would make the distribution meaningless: turn 1 of a session and turn 20 are not
     * samples of the same thing, and averaging them hides exactly the growth this exists to show.
     */
    public void sessionFinished(InterviewSession session) {
        registry.counter("interview.sessions",
                "state", String.valueOf(session.getState()),
                "reason", String.valueOf(session.getTerminalReason())).increment();
        registry.summary("interview.prompt_tokens").record(session.getPromptTokens());
        registry.summary("interview.completion_tokens").record(session.getCompletionTokens());
        registry.summary("interview.turns").record(session.getTurnCount());
        registry.summary("interview.tool_calls").record(session.getToolCallCount());
    }
}
