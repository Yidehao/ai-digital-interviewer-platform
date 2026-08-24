package org.interviewer.entity.bo;

import lombok.Data;

/**
 * A human's own assessment of a candidate, recorded alongside the model's.
 *
 * <p><b>The reviewer's scores are asked for, not just an approval.</b> A console that shows the
 * model's verdict and offers an "approve" button produces rubber-stamping: the measured failure of
 * decision-support tools is that people agree with them, especially when agreeing is one click and
 * disagreeing requires justifying yourself. Asking the reviewer to score the candidate themselves,
 * <em>before</em> the model's numbers are revealed, means the human judgement exists independently
 * rather than as an endorsement.
 *
 * <p>It also produces something the project needs anyway: every reviewed session becomes another
 * labeled data point. The human ceiling measured on twelve authored transcripts can grow into one
 * measured on real interviews, without anyone being asked to do extra work.
 */
@Data
public class ReviewDecisionBO {

    private String sessionId;

    /** Who is signing off. Free text for now; a real deployment resolves this from the session. */
    private String reviewedBy;

    /** The reviewer's own 1-5 scores, given before the model's are shown. */
    private Integer overall;
    private Integer correctness;
    private Integer depth;
    private Integer communication;
    private Integer practicalExperience;

    /** advance / reject / needs_another_round — the reviewer's decision, never the model's. */
    private String decision;

    private String notes;
}
