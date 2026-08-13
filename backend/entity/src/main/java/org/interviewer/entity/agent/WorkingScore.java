package org.interviewer.entity.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The interviewer's private working note about one competency, from {@code score_response}.
 *
 * <p><b>This is not the grade.</b> It steers the interviewer's own planning - what to probe next,
 * what is already covered - and it never reaches the grader, is never persisted as a score, and is
 * never shown to the candidate. If it did reach the grader, the grader would no longer be an
 * independent second opinion; it would be agreeing with a number it was handed.
 *
 * <p>Keyed by {@code (questionId, dimension)}: scoring the same pair twice overwrites.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkingScore {

    private String questionId;

    private String dimension;

    private int score;

    private String evidence;

    private String confidence;

    public String key() {
        return questionId + "|" + dimension;
    }
}
