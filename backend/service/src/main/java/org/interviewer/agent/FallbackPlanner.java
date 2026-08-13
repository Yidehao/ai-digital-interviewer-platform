package org.interviewer.agent;

import org.interviewer.entity.agent.FallbackReason;
import org.interviewer.entity.ollama.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * What to say back to the model when a turn goes wrong.
 *
 * <p>The rule this class exists to enforce: <b>every rung has a deterministic, non-LLM action.</b>
 * Nowhere does the loop write {@code while (!gotToolCall) retry()}. One nudge, then the ladder,
 * then a clean close. A retry loop against a model that is confused produces a longer, more
 * expensive version of the same confusion, and it is the reason "the agent hung" is the most
 * common failure mode of hand-rolled agent loops.
 *
 * <p>The messages here are the deterministic half of each rung. The half that changes state —
 * serving a question, closing the session — is {@code InterviewerAgent}'s, because it needs the
 * session.
 */
@Component
public class FallbackPlanner {

    /**
     * Rung 2 — schema-invalid arguments.
     *
     * <p>The validation messages go back <b>verbatim</b>. They name the offending field, and a
     * single repair turn only works if the model is told what to fix. In the Phase 0.5 benchmark
     * this recovered 84% of invalid calls; a second repair recovered none, which is why the budget
     * is one.
     */
    public ChatMessage invalidArgs(String toolName, List<String> validationErrors) {
        return ChatMessage.toolResult(toolName, """
                {"error":"invalid_arguments","tool":"%s","validation_errors":%s,\
                "instruction":"Call the tool again with corrected arguments. \
                Fix only the fields named above."}"""
                .formatted(toolName, jsonArray(validationErrors)));
    }

    /**
     * Rung 3 — a tool name we do not serve.
     *
     * <p>Listing the real names is the whole recovery: a model that invented {@code summarise_note}
     * usually meant one of the six, and cannot guess which without being told.
     */
    public ChatMessage unknownTool(String attempted, List<String> available) {
        return ChatMessage.toolResult(attempted, """
                {"error":"unknown_tool","attempted":"%s","available":%s,\
                "instruction":"Call one of the available tools."}"""
                .formatted(escape(attempted), jsonArray(available)));
    }

    /**
     * Rung 4 — the tool threw, or the loop's timeout fired.
     *
     * <p>Note what this is not: a {@code run_code} timeout does not come through here. Code that
     * hangs is data about the candidate and arrives as a normal result with {@code timedOut:true}.
     * This is for genuine faults.
     */
    public ChatMessage toolError(String toolName, String code, String detail) {
        return ChatMessage.toolResult(toolName, """
                {"error":"tool_failed","tool":"%s","code":"%s","detail":"%s",\
                "instruction":"Do not call this tool again with the same arguments. \
                Continue the interview."}"""
                .formatted(escape(toolName), escape(code), escape(detail)));
    }

    /**
     * Rung 5 — our result failed our own schema.
     *
     * <p>This one is our bug, not the model's, and the detail is deliberately withheld: telling the
     * model which of our fields is malformed invites it to try to compensate for our internal
     * error, which produces stranger behaviour than simply moving on. It is logged at ERROR
     * server-side, where someone can act on it.
     */
    public ChatMessage internalError(String toolName) {
        return ChatMessage.toolResult(toolName, """
                {"error":"tool_internal_error","tool":"%s",\
                "instruction":"This is a server-side fault, not a problem with your call. \
                Continue the interview."}"""
                .formatted(escape(toolName)));
    }

    /**
     * Rung 6 — the identical call twice running.
     *
     * <p>Naming the repetition and forbidding it works where a bare error does not: a model that
     * repeats itself is usually waiting for a state change that the repeated call cannot cause.
     */
    public ChatMessage repeatedCall(String toolName) {
        return ChatMessage.toolResult(toolName, """
                {"error":"repeated_call","tool":"%s",\
                "instruction":"You just made this exact call with these exact arguments and the \
                result has not changed. Do something different: ask a follow-up, fetch the next \
                question, or finish the interview."}"""
                .formatted(escape(toolName)));
    }

    /**
     * Rung 1 — no tool call. One nudge, and only one.
     *
     * <p>A second consecutive failure skips this entirely and goes to the deterministic branch:
     * a model that ignored the instruction once will ignore it twice, and the interview should
     * move forward without it rather than repeat the request.
     */
    public ChatMessage noToolCallNudge() {
        return ChatMessage.user("""
                You replied with prose. Prose does not reach the candidate and does not advance \
                the interview. Call exactly one tool now.""");
    }

    /** Told to the model after the loop has served a question on its behalf. */
    public ChatMessage servedQuestionForYou(String toolName) {
        return ChatMessage.toolResult(toolName, """
                {"note":"no_tool_call_recovered","action":"the next question was served for you",\
                "instruction":"Continue the interview from the candidate's next answer."}""");
    }

    /** The canned closing used when the loop, not the model, ends the interview. */
    public String closingMessageFor(FallbackReason reason) {
        return switch (reason) {
            case BUDGET -> "That is all the time we have for today. "
                    + "Thank you for talking me through your experience.";
            case ERROR_BUDGET, RESULT_SCHEMA_INVALID -> "We are going to stop here. "
                    + "Thank you for your time - your answers so far have been recorded.";
            case MODEL_UNREACHABLE -> "Thank you for completing the interview. "
                    + "Your answers have been recorded and will be reviewed.";
            case NO_TOOL_CALL, NO_TOOL_CALL_REPEATED -> "That covers what we needed. "
                    + "Thank you for your time.";
            default -> "Thank you for your time. This concludes the interview.";
        };
    }

    private static String jsonArray(List<String> values) {
        return values.stream()
                .map(v -> "\"" + escape(v) + "\"")
                .reduce((a, b) -> a + "," + b)
                .map(joined -> "[" + joined + "]")
                .orElse("[]");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ");
    }
}
