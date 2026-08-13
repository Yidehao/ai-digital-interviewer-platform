package org.interviewer.agent;

import lombok.extern.slf4j.Slf4j;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.ollama.ChatMessage;
import org.interviewer.utils.AgentProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounds how much conversation goes to the model, and assembles the prompt.
 *
 * <p>Two forces pull in opposite directions here, and the resolution is the interesting part.
 *
 * <p><b>Context growth is the latency killer.</b> {@code messages} grows every turn, prompt
 * evaluation is the dominant term in first-token latency, and prompt evaluation is linear in
 * prompt length. A latency figure measured at turn 2 does not survive to turn 20 without a window.
 *
 * <p><b>But eviction fights the prefix cache.</b> Dropping the oldest message changes the prompt
 * prefix, which invalidates Ollama's cached KV state from that point on — and that cache is worth
 * 20-75x on this hardware. Trimming one message per turn would forfeit the cache on every single
 * turn, which is strictly worse than not trimming at all.
 *
 * <p>So eviction is <b>rare and large</b>: nothing happens until the history exceeds
 * {@code evictAbove}, and then it drops back to {@code keepAfter} in one step. Most turns change
 * nothing at the front of the prompt and stay warm; occasionally one turn pays for a re-evaluation
 * and buys many cheap turns after it.
 *
 * <p>Eviction lands only on exchange boundaries — a candidate answer — so an assistant message
 * carrying tool calls is never separated from the tool results that answer it. An orphaned tool
 * result is a conversation the model has no way to make sense of.
 */
@Slf4j
@Component
public class ConversationWindow {

    private final AgentProperties properties;

    public ConversationWindow(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * The full prompt for one model call: immutable prefix first, mutable tail last.
     *
     * <pre>
     *   system prompt  ->  [tool schemas, carried in the request's tools array]
     *                  ->  rolling summary (Phase 3+)
     *                  ->  windowed conversation
     * </pre>
     *
     * <p>The system prompt is taken from the session verbatim rather than rebuilt, because a
     * rebuild that differs by so much as a space costs a full prompt re-evaluation.
     */
    public List<ChatMessage> assemble(InterviewSession session) {
        List<ChatMessage> prompt = new ArrayList<>(session.getMessages().size() + 2);
        if (session.getSystemPrompt() != null) {
            prompt.add(ChatMessage.system(session.getSystemPrompt()));
        }
        prompt.addAll(session.getMessages());
        return prompt;
    }

    /**
     * Evict old history if, and only if, it has grown past the threshold.
     *
     * @return how many messages were dropped; zero on the overwhelming majority of turns
     */
    public int evictIfNeeded(InterviewSession session) {
        List<ChatMessage> messages = session.getMessages();
        int evictAbove = evictAbove();
        if (messages.size() <= evictAbove) {
            return 0;
        }

        int target = keepAfter();
        int dropTo = messages.size() - target;

        // Advance to the next exchange boundary so tool results keep the tool calls they answer.
        int boundary = dropTo;
        while (boundary < messages.size() && !isExchangeBoundary(messages.get(boundary))) {
            boundary++;
        }
        if (boundary >= messages.size()) {
            // No boundary ahead of the target - one very long exchange. Leave it rather than
            // slicing it apart; the budgets will end the interview before this matters.
            return 0;
        }

        List<ChatMessage> kept = new ArrayList<>(messages.subList(boundary, messages.size()));
        session.setMessages(kept);
        log.debug("session {} evicted {} messages, {} remain",
                session.getSessionId(), boundary, kept.size());
        return boundary;
    }

    /**
     * A candidate answer starts a new exchange. Cutting here keeps every assistant tool-call
     * message with the tool results that follow it.
     */
    private boolean isExchangeBoundary(ChatMessage message) {
        return "user".equals(message.getRole());
    }

    int evictAbove() {
        return Math.max(8, properties.getHistoryWindow() * 8);
    }

    int keepAfter() {
        return Math.max(4, properties.getHistoryWindow() * 4);
    }
}
