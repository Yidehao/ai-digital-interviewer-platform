package org.interviewer.agent;

import org.interviewer.entity.agent.FallbackReason;
import org.interviewer.entity.agent.SessionState;
import org.interviewer.entity.agent.Turn;

/**
 * Everything the outside world learns about a running interview.
 *
 * <p><b>This callback is the seam.</b> {@code InterviewerAgent.run(session, events)} knows nothing
 * about SSE, Tomcat, or {@code SseEmitter}; production passes an implementation that writes to an
 * emitter, and the offline eval harness passes {@link NoOpAgentEvents} and gets byte-for-byte
 * identical behaviour. Without that, evaluating the agent would mean running a servlet container,
 * and "the eval measures the same code path as production" would be an assertion rather than a
 * consequence of the design.
 *
 * <p>Every method has a default no-op so an implementation only overrides what it uses.
 */
public interface AgentEvents {

    /** A scripted question was served. {@code videoSrc} is null for follow-ups. */
    default void onQuestion(Turn turn, String videoSrc) {
    }

    /** A model-generated probe was delivered. Text only - no avatar clip exists for these. */
    default void onFollowup(Turn turn) {
    }

    default void onToolStart(String toolName) {
    }

    default void onToolEnd(String toolName, long durationMs, boolean ok) {
    }

    /** One token of model output, for streaming. Unused until the streaming path lands. */
    default void onToken(String delta) {
    }

    /**
     * A rung of the fallback ladder fired. Worth surfacing rather than only counting: a fallback
     * is the loop working, but a sustained fallback rate is the "model-driven" framing failing.
     */
    default void onFallback(FallbackReason reason, String detail) {
    }

    default void onFinished(SessionState state, String closingMessage) {
    }
}
