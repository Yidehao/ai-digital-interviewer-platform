package org.interviewer.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * One row of the review queue.
 *
 * <p>Deliberately does <b>not</b> carry the scores. The review console shows a reviewer the
 * transcript before it shows them the model's numbers, because a list that leads with "overall: 2"
 * has already made the decision for them — automation bias is the well-documented failure of
 * exactly this kind of tool, and a queue sorted by machine score is the shape that causes it.
 *
 * <p>What the queue does carry is what a reviewer needs to <em>triage</em>: who, for which job, how
 * long ago, whether the samples disagreed, and whether anyone has looked yet.
 */
@Data
public class VerdictSummaryVO {

    private String sessionId;
    private String candidateId;
    private String candidateName;
    private String jobName;

    /** Whether repeated grading disagreed with itself. The reason this row may need a person. */
    private Boolean needsHumanReview;
    private Integer dimensionSpread;
    private Integer samples;

    private String reviewedBy;
    private LocalDateTime reviewedTime;
    private LocalDateTime createdTime;
}
