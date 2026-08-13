package org.interviewer.agent.tool;

import org.interviewer.agent.AgentEvents;
import org.interviewer.agent.NoOpAgentEvents;
import org.interviewer.entity.agent.InterviewSession;

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
     * The session being mutated.
     *
     * <p>For a detached context this is a real, empty {@code InterviewSession} that is thrown away
     * afterwards. That is the whole trick: tools write to it exactly as they always do, and only
     * persistence differs. The alternative - a nullable session and a null check in six
     * implementations - puts the same branch in six places and gets it wrong in one of them.
     */
    InterviewSession session();

    /**
     * Where a tool reports what it did.
     *
     * <p>On the context rather than on the session because the session is serialised to Redis as
     * JSON and a callback cannot be. Tools that deliver something to the candidate - a question, a
     * follow-up - emit here; everything else leaves it alone.
     */
    default AgentEvents events() {
        return NoOpAgentEvents.INSTANCE;
    }

    /**
     * True when there is no real interview behind this call: writes are discarded and nothing is
     * persisted. Tools should still return their normal result shape.
     */
    boolean detached();

    /** A throwaway context, for MCP callers with no interview and for tests. */
    static ToolContext detached(String sessionId) {
        InterviewSession scratch = new InterviewSession();
        scratch.setSessionId(sessionId);
        return new ToolContext() {
            @Override
            public String sessionId() {
                return sessionId;
            }

            @Override
            public InterviewSession session() {
                return scratch;
            }

            @Override
            public boolean detached() {
                return true;
            }
        };
    }
}
