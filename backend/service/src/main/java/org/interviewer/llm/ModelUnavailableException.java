package org.interviewer.llm;

/**
 * The model could not be reached.
 *
 * <p>Deliberately distinct from "the model answered with something unusable". That distinction is
 * the difference between rung 9 of the fallback ladder — degrade to the scripted pipeline, the
 * candidate still completes the interview, the grader still runs — and rungs 1 to 6, which are
 * ordinary in-conversation recovery. Collapsing the two would either degrade a whole interview
 * over one malformed tool call, or keep retrying a model that is not running.
 */
public class ModelUnavailableException extends RuntimeException {

    public ModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ModelUnavailableException(String message) {
        super(message);
    }
}
