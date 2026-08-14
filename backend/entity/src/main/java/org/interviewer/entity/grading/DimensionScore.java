package org.interviewer.entity.grading;

/**
 * One competency, scored.
 *
 * <p><b>Field order is load-bearing and matches the JSON schema.</b> With constrained decoding the
 * model emits fields in schema order, so {@code evidence} then {@code reasoning} then {@code score}
 * forces it to quote the candidate and argue before committing to a number. Putting {@code score}
 * first would let it pick a number and then write a justification for it, which is a different and
 * much worse cognitive task — the reasoning becomes rationalisation.
 */
public record DimensionScore(String name, String evidence, String reasoning, int score) {
}
