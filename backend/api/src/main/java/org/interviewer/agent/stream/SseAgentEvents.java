package org.interviewer.agent.stream;

import lombok.extern.slf4j.Slf4j;
import org.interviewer.agent.AgentEvents;
import org.interviewer.entity.agent.FallbackReason;
import org.interviewer.entity.agent.SessionState;
import org.interviewer.entity.agent.Turn;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pushes agent events to the candidate's browser.
 *
 * <p>This class is the payoff for {@link AgentEvents} existing at all. {@code InterviewerAgent}
 * has never known what SSE is: it reports through a callback, production passes this, and the
 * offline eval harness passes {@code NoOpAgentEvents}. That is why "the eval measures the same code
 * path as production" is a consequence of the design rather than something to be careful about.
 *
 * <p><b>What reaches the candidate is deliberately narrower than what the loop knows.</b> Questions,
 * follow-ups and the closing message go out. Tool names, fallback reasons, working scores and
 * evidence do not — a candidate who could see {@code score_response(depth, 2)} arriving mid-answer
 * is being shown the interviewer's private opinion of them while they are still talking, and a
 * candidate who could see {@code fallback: NO_TOOL_CALL} learns the system is struggling. Neither
 * helps them and both change how they answer.
 *
 * <p>Tool and fallback events are still emitted, but only on the operator channel — the log and the
 * metrics — which is what {@code onToolStart}/{@code onFallback} do here.
 */
@Slf4j
public class SseAgentEvents implements AgentEvents {

    private final SessionEmitter emitter;
    private final AgentMetrics metrics;

    public SseAgentEvents(SessionEmitter emitter, AgentMetrics metrics) {
        this.emitter = emitter;
        this.metrics = metrics;
    }

    @Override
    public void onQuestion(Turn turn, String videoSrc) {
        emitter.send("question", payload(turn, videoSrc));
    }

    @Override
    public void onFollowup(Turn turn) {
        // videoSrc is null by design: a follow-up did not exist until a moment ago, so no avatar
        // clip was ever rendered for it. The client keeps its neutral idle loop and overlays text.
        emitter.send("question", payload(turn, null));
    }

    @Override
    public void onToken(String delta) {
        emitter.send("token", delta);
    }

    @Override
    public void onToolStart(String toolName) {
        // Operator-visible only. The candidate is told what to answer, never how the machine
        // decided to ask it.
        metrics.toolStarted(toolName);
    }

    @Override
    public void onToolEnd(String toolName, long durationMs, boolean ok) {
        metrics.toolFinished(toolName, durationMs, ok);
    }

    @Override
    public void onFallback(FallbackReason reason, String detail) {
        // The metric that decides whether "model-driven" survives contact with reality.
        metrics.fallback(reason);
        log.info("fallback {} - {}", reason, detail);
    }

    @Override
    public void onFinished(SessionState state, String closingMessage) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("state", state == null ? null : state.name());
        data.put("closingMessage", closingMessage);
        emitter.send("done", data);
        emitter.complete();
    }

    private Map<String, Object> payload(Turn turn, String videoSrc) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("turnId", turn.getSeq());
        data.put("questionId", turn.getQuestionId());
        data.put("text", turn.getText());
        data.put("videoSrc", videoSrc);
        // Deliberately absent: turn.getKind(). Telling the client "this one is a FOLLOWUP" would
        // show the candidate that the interviewer decided to probe them, which is the interviewer's
        // judgement about their last answer.
        return data;
    }
}
