package org.interviewer.agent.tool;

/**
 * What a tool is allowed to know about the interview it is running inside.
 *
 * <p>Deliberately narrow. Tools receive this rather than the {@code InterviewSession} itself so
 * that the MCP facade can hand them a detached context — an MCP client calling
 * {@code fetch_question} from Inspector has no session, and the alternative to this seam is a
 * nullable session parameter with null checks scattered through six implementations.
 *
 * <p>Phase 2 widens this as the session model lands. It stays an interface, not a class, so the
 * detached implementation can discard writes without any tool knowing.
 */
public interface ToolContext {

    /** Correlation id. For a detached context this is a generated id that outlives nothing. */
    String sessionId();

    /**
     * True when there is no real interview behind this call: writes are discarded and nothing is
     * persisted. Tools should still return their normal result shape.
     */
    boolean detached();
}
