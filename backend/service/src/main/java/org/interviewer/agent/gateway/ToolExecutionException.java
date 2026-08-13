package org.interviewer.agent.gateway;

/**
 * A tool threw, or the loop's timeout fired on it. Rung 4.
 *
 * <p>{@code code} is a short machine-readable token that goes back to the model as part of the
 * structured error - {@code timeout}, {@code tool_threw}, {@code unknown_tool}. The model can act
 * on "timeout" by moving on; it cannot act on a Java stack trace.
 */
public class ToolExecutionException extends RuntimeException {

    private final String code;

    public ToolExecutionException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ToolExecutionException(String code, String message) {
        this(code, message, null);
    }

    public String code() {
        return code;
    }
}
