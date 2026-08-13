package org.interviewer.agent.tool.dto;

/**
 * Result of {@code run_code}. Mirrors {@code tools/run_code.result.json}.
 *
 * <p>The same six fields for every outcome. A timeout is {@code exitCode=124, timedOut=true}
 * rather than an error envelope: the candidate's code hanging is data about the candidate, and the
 * model needs to be able to reason about it as an interview event rather than a system fault.
 */
public record RunCodeResult(
        String stdout,
        String stderr,
        int exitCode,
        boolean timedOut,
        boolean truncated,
        long durationMs) {
}
