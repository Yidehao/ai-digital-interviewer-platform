package org.interviewer.agent.tool.dto;

/**
 * Arguments for {@code score_response}. Mirrors {@code tools/score_response.json}.
 *
 * <p>{@code confidence} is optional and defaults to medium. In v1 it was required and was omitted
 * on 17 of 60 benchmark calls, each of which then needed a repair turn to add a field that steers
 * nothing on its own.
 */
public record ScoreResponseArgs(
        String questionId,
        String dimension,
        int score,
        String evidence,
        String confidence) {
    public ScoreResponseArgs {
        if (confidence == null) {
            confidence = "medium";
        }
    }
}
