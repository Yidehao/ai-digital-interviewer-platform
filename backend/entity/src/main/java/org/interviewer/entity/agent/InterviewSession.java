package org.interviewer.entity.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.interviewer.entity.ollama.ChatMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One interview in flight.
 *
 * <p>The session holds <b>two physically separate structures</b>, and that separation is the whole
 * of the grader-isolation claim:
 *
 * <ul>
 *   <li>{@link #messages} — what goes to the model. Grows every turn: tool calls, tool results,
 *       repair prompts, nudges.</li>
 *   <li>{@link #turns} — the transcript. <b>The grader's only input.</b></li>
 * </ul>
 *
 * <p>The claim is not "we filter the interviewer's notes out before grading." It is that the
 * grader is never handed the structure containing them. A filter is a line of code someone can
 * later get wrong; two types are not.
 *
 * <p>{@link #workingScores} and {@link #evidence} live on the interviewer side and reach neither
 * the grader nor the candidate.
 *
 * <p>This is the hot, Redis-backed object. The MyBatis row that outlives it is a separate type on
 * purpose — merging them would leak persistence annotations into agent state and let
 * {@code update-strategy: not_empty} silently eat fields that are legitimately empty.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InterviewSession {

    private String sessionId;

    private String candidateId;

    private String jobId;

    private String interviewerId;

    private SessionState state = SessionState.CREATED;

    private Instant startedAt;

    private Instant finishedAt;

    // ------------------------------------------------------------------ model side

    /** What the model sees. Never given to the grader. */
    private List<ChatMessage> messages = new ArrayList<>();

    /**
     * The system prompt, built once and reused as the identical byte string.
     *
     * <p>Rebuilding it per turn — even a whitespace difference — busts Ollama's prefix cache and
     * silently costs a full prompt re-evaluation, measured at ~20 s on a 1.5k-token prompt on this
     * hardware. That is a 60x regression from a reformat, with no error to notice.
     */
    private String systemPrompt;

    // ------------------------------------------------------------------ transcript side

    /** The grader's only input. */
    private List<Turn> turns = new ArrayList<>();

    // ------------------------------------------------------------------ interviewer's private notes

    /** Keyed {@code questionId|dimension}; scoring the same pair twice overwrites. */
    private Map<String, WorkingScore> workingScores = new LinkedHashMap<>();

    private List<Evidence> evidence = new ArrayList<>();

    // ------------------------------------------------------------------ bookkeeping

    private List<String> servedQuestionIds = new ArrayList<>();

    /** Follow-ups asked per parent question, for the 2-per-question cap. */
    private Map<String, Integer> followupCounts = new LinkedHashMap<>();

    private int turnCount;

    private int toolCallCount;

    private int errorCount;

    private int consecutiveNoToolCalls;

    /**
     * Repairs offered for the current run of invalid tool calls, reset by any successful call.
     *
     * <p>Separate from {@link #consecutiveNoToolCalls} on purpose: prose and bad arguments are
     * different failures with different recoveries, and sharing a counter would let one rung
     * consume the other's budget.
     */
    private int repairCount;

    /** Hash of the last (tool, args) pair, for rung 6. */
    private String lastToolCallHash;

    /** Why the interview ended, when it did not end by the model's own choice. */
    private FallbackReason terminalReason;

    private String closingMessage;

    /** Rung tallies, for {@code interview_fallback_total{reason}}. */
    private Map<FallbackReason, Integer> fallbackCounts = new LinkedHashMap<>();

    /**
     * Every tool call, in order. This is what the "adaptive" claim is checked against: two
     * candidates who answer differently should produce different sequences here.
     */
    private List<ToolRecord> toolLog = new ArrayList<>();

    /**
     * Summed {@code prompt_eval_count}. Prompt evaluation dominates first-token latency, so this
     * growing faster than the interview does is the signal that the sliding window is not working.
     */
    private int promptTokens;

    /**
     * Nanoseconds Ollama actually spent evaluating prompts this session.
     *
     * <p>Kept alongside {@link #promptTokens} because the two answer different questions and only
     * one of them is about cost. {@code prompt_eval_count} reports the <em>whole</em> prompt on
     * every call whether or not the prefix was cached — measured here, an identical repeat call
     * reported the same 745 tokens while its evaluation time fell from 6.89 s to 0.09 s. So a
     * token ratio derived from the count describes prompt SIZE, and reading it as work done
     * overstates the cost of history by whatever the cache is saving, which was 76x.
     */
    private long promptEvalNanos;

    private int completionTokens;

    /**
     * One tool call as it happened.
     *
     * @param outcome        OK, SCHEMA_REJECTED, ERROR or TIMEOUT
     * @param fallbackReason which ladder rung fired, when one did
     */
    public record ToolRecord(String toolName,
                             String argsJson,
                             String outcome,
                             String fallbackReason,
                             long durationMs) {
    }

    // ------------------------------------------------------------------ behaviour

    public int nextSeq() {
        return turns.size();
    }

    public Turn addTurn(TurnKind kind, String questionId, String text, Instant at) {
        Turn turn = Turn.builder()
                .seq(nextSeq())
                .kind(kind)
                .questionId(questionId)
                .text(text)
                .startedAt(at)
                .endedAt(at)
                .build();
        turns.add(turn);
        return turn;
    }

    public void recordFallback(FallbackReason reason) {
        fallbackCounts.merge(reason, 1, Integer::sum);
    }

    public int followupCount(String parentQuestionId) {
        return followupCounts.getOrDefault(parentQuestionId, 0);
    }

    public int totalFollowups() {
        return followupCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    @JsonIgnore
    public boolean isTerminal() {
        return state != null && state.isTerminal();
    }

    /** The transcript turns the grader will receive. Never {@link #messages}. */
    @JsonIgnore
    public List<Turn> transcript() {
        return List.copyOf(turns);
    }
}
