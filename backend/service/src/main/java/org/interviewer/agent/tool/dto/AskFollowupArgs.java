package org.interviewer.agent.tool.dto;

/**
 * Arguments for {@code ask_followup}. Mirrors {@code tools/ask_followup.json}.
 *
 * <p>Only {@code question} and {@code parentQuestionId} are required. v1 required all four and
 * cost a repair turn on nearly every call to the tool the model reaches for most — see
 * {@code eval/tool-design-findings.md}. Every required field costs emission reliability.
 */
public record AskFollowupArgs(
        String question,
        String rationale,
        String parentQuestionId,
        String competency) {
}
