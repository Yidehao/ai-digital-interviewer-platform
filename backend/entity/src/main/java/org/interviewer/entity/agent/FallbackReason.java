package org.interviewer.entity.agent;

/**
 * The rungs of the fallback ladder, as a metric label.
 *
 * <p>Every rung has a deterministic, non-LLM action. Exposed as
 * {@code interview_fallback_total{reason}} from Phase 6 - deliberately early, because a fallback
 * rate above roughly 5% would mean the model is not reliably driving the interview and the
 * "model-driven" framing needs softening. That is much better to learn at Phase 6 than after
 * three more phases are built on top of it.
 */
public enum FallbackReason {

    /** Rung 1 - the model wrote prose instead of calling a tool. */
    NO_TOOL_CALL,

    /** Rung 1b - twice in a row. Terminal. */
    NO_TOOL_CALL_REPEATED,

    /** Rung 2 - arguments failed their schema. One repair is offered. */
    INVALID_ARGS,

    /** Rung 3 - a tool name we do not serve. */
    UNKNOWN_TOOL,

    /** Rung 4 - the tool threw, or the loop's timeout fired. */
    TOOL_ERROR,

    /** Rung 5 - our result failed our own schema. Our bug, not the model's. */
    RESULT_SCHEMA_INVALID,

    /** Rung 6 - the identical (tool, args) call twice running. */
    REPEATED_CALL,

    /** Rung 7 - too many errors. Terminal. */
    ERROR_BUDGET,

    /** Rung 8 - turn count or wall clock exhausted. Terminal. */
    BUDGET,

    /** Rung 9 - Ollama unreachable. Degrade to the scripted pipeline; the candidate still finishes. */
    MODEL_UNREACHABLE,

    /**
     * The candidate stopped answering.
     *
     * <p>Not a model failure and not a bug - a person closed their laptop, or their connection
     * died. It is a rung because the alternative is a session that occupies a thread and a slot in
     * the concurrency budget until the 45-minute wall clock retires it.
     */
    CANDIDATE_TIMEOUT
}
