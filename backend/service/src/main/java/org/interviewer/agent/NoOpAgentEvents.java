package org.interviewer.agent;

/**
 * Discards every event.
 *
 * <p>What the eval harness passes, and what makes the offline path identical to production rather
 * than merely similar.
 */
public final class NoOpAgentEvents implements AgentEvents {

    public static final NoOpAgentEvents INSTANCE = new NoOpAgentEvents();

    private NoOpAgentEvents() {
    }
}
