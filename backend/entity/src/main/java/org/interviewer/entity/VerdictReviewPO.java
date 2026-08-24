package org.interviewer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * One human's assessment of one interview.
 *
 * <p>Keyed on (session, reviewer) rather than session alone, so a second reviewer is a second row
 * instead of an overwrite. That is not tidiness — it is what makes inter-rater agreement on
 * <em>real</em> interviews computable later, turning the twelve-transcript pilot into something
 * measured on production data as a by-product of review work already being done.
 */
@Data
@TableName("verdict_review")
public class VerdictReviewPO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String reviewedBy;

    /** The reviewer's own scores, given before the model's were revealed. */
    private Integer overall;
    private Integer correctness;
    private Integer depth;
    private Integer communication;
    private Integer practicalExperience;

    /** advance / reject / needs_another_round. The reviewer's, never the model's. */
    private String decision;

    private String notes;
    private LocalDateTime createdTime;
}
