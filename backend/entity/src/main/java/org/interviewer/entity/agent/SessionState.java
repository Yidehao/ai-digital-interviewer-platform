package org.interviewer.entity.agent;

/**
 * Where an interview session is in its life.
 *
 * <p>{@link #isTerminal()} is the assertion every fallback-ladder test makes. Eleven tests, one
 * per rung, each ending in "the session reached a terminal state" - that repeated assertion is
 * what the "the loop never stalls" claim actually rests on.
 */
public enum SessionState {

    CREATED,

    RUNNING,

    /** finish_interview was called, by the model or by the loop. */
    FINISHED,

    /** The model was unreachable; questions were served from the bank with no model in the path. */
    DEGRADED,

    /** The loop could not continue and could not close the interview cleanly. */
    FAILED;

    public boolean isTerminal() {
        return this == FINISHED || this == DEGRADED || this == FAILED;
    }
}
