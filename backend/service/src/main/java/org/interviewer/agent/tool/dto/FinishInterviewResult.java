package org.interviewer.agent.tool.dto;

/**
 * Result of {@code finish_interview}. Mirrors {@code tools/finish_interview.result.json}.
 *
 * <p>The only terminal tool. Calling it twice is {@code alreadyFinished=true} rather than a
 * failure, so a duplicate call at the end of a session costs nothing.
 */
public record FinishInterviewResult(
        boolean finished,
        String reason,
        boolean alreadyFinished,
        int questionsAsked,
        boolean gradingQueued) {
}
