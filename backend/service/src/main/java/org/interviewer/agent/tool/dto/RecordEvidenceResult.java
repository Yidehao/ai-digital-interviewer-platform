package org.interviewer.agent.tool.dto;

/**
 * Result of {@code record_evidence}. Mirrors {@code tools/record_evidence.result.json}.
 *
 * <p>A quote matching no answer turn comes back {@code accepted=false}, which is the guard against
 * fabricated quotes. Matching is normalised and similarity-based rather than literal containment:
 * STT punctuation and casing vary, and models near-quote rather than quote, so a strict check
 * rejects legitimate calls and burns the error budget on what should be a useful guard.
 */
public record RecordEvidenceResult(
        boolean accepted,
        String evidenceId,
        String competency,
        Integer matchedTurnSeq,
        Double similarity,
        String reason) {
}
