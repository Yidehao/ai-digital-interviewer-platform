package org.interviewer.agent.tool.dto;

/**
 * Result of {@code ask_followup}. Mirrors {@code tools/ask_followup.result.json}.
 *
 * <p>Hitting the follow-up cap (2 per question, 6 per session) is {@code delivered=false} with a
 * {@code reason}, not an exception. The model can act on a refusal; it cannot act on a stack
 * trace.
 */
public record AskFollowupResult(
        boolean delivered,
        String turnId,
        String text,
        int followupCount,
        String reason) {
}
