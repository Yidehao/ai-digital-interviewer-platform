package org.interviewer.agent.tool.dto;

/**
 * Result of {@code score_response}. Mirrors {@code tools/score_response.result.json}.
 *
 * <p>This is the interviewer's private working note, not the grade. It never reaches the grader
 * and is never persisted as a score.
 *
 * <p>{@code supersededPrevious} exists to break a loop: the tool is idempotent on
 * {@code (questionId, dimension)}, and telling the model it has already scored that pair is what
 * stops it scoring the same thing repeatedly.
 */
public record ScoreResponseResult(
        boolean recorded,
        String questionId,
        String dimension,
        int score,
        boolean supersededPrevious,
        int dimensionsScored) {
}
