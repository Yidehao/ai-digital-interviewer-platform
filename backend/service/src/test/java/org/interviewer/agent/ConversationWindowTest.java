package org.interviewer.agent;

import org.interviewer.agent.support.Fixtures;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.ollama.ChatMessage;
import org.interviewer.utils.AgentProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The window has to satisfy two requirements that pull against each other: bound the prompt, and
 * do not fight the prefix cache. These tests pin the resolution.
 */
class ConversationWindowTest {

    private final AgentProperties properties = new AgentProperties();
    private final ConversationWindow window = new ConversationWindow(properties);

    @Test
    @DisplayName("the system prompt leads the assembled prompt, taken verbatim from the session")
    void assemblePutsTheImmutablePrefixFirst() {
        InterviewSession session = Fixtures.session();
        session.getMessages().add(ChatMessage.user("an answer"));

        var prompt = window.assemble(session);

        assertThat(prompt.get(0).getRole()).isEqualTo("system");
        assertThat(prompt.get(0).getContent()).isEqualTo(session.getSystemPrompt());
        assertThat(prompt).hasSize(2);
    }

    @Test
    @DisplayName("short conversations are never evicted, so most turns stay cache-warm")
    void nothingIsEvictedBelowTheThreshold() {
        InterviewSession session = Fixtures.session();
        for (int i = 0; i < window.evictAbove(); i++) {
            session.getMessages().add(ChatMessage.user("answer " + i));
        }

        assertThat(window.evictIfNeeded(session)).isZero();
        assertThat(session.getMessages()).hasSize(window.evictAbove());
    }

    @Test
    @DisplayName("eviction is rare and large rather than one message per turn")
    void evictionDropsAWholeBlockAtOnce() {
        InterviewSession session = Fixtures.session();
        for (int i = 0; i < window.evictAbove() + 5; i++) {
            session.getMessages().add(ChatMessage.user("answer " + i));
        }
        int before = session.getMessages().size();

        int dropped = window.evictIfNeeded(session);

        // Trimming a single message per turn would change the prefix on every turn and forfeit
        // the KV cache every time - strictly worse than not trimming at all.
        assertThat(dropped).isGreaterThan(1);
        assertThat(session.getMessages()).hasSizeLessThan(before);
        // And having just evicted, the next turn evicts nothing.
        assertThat(window.evictIfNeeded(session)).isZero();
    }

    @Test
    @DisplayName("eviction cuts at a candidate answer, never between a tool call and its result")
    void evictionRespectsExchangeBoundaries() {
        InterviewSession session = Fixtures.session();
        for (int i = 0; i < 12; i++) {
            session.getMessages().add(ChatMessage.user("answer " + i));
            session.getMessages().add(ChatMessage.assistantToolCalls(java.util.List.of()));
            session.getMessages().add(ChatMessage.toolResult("fetch_question", "{}"));
        }

        window.evictIfNeeded(session);

        // An orphaned tool result is a conversation the model cannot make sense of.
        assertThat(session.getMessages().get(0).getRole()).isEqualTo("user");
    }
}
