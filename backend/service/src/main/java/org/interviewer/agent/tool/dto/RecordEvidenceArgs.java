package org.interviewer.agent.tool.dto;

/**
 * Arguments for {@code record_evidence}. Mirrors {@code tools/record_evidence.json}.
 */
public record RecordEvidenceArgs(
        String competency,
        String quote,
        String judgment,
        String questionId) {
}
