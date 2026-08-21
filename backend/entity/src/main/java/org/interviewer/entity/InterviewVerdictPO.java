package org.interviewer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * One agent interview's verdict, stored structurally.
 *
 * <p>The scripted pipeline writes an HTML blob into {@code interview_record.result}. That renders
 * and does nothing else: it cannot be aggregated, two candidates cannot be compared on one
 * dimension, and agreement against a human label would mean parsing prose back out of markup.
 *
 * <p>The agent grader already produces structure — four dimensions, 1 to 5, each with the evidence
 * behind it — because the verdict schema is enforced at decode time. Keeping that structure is what
 * will let {@code eval/labeling_sheet.csv} be joined to real sessions once real sessions exist.
 *
 * <p>{@code sessionId} is the primary key, so re-grading replaces rather than accumulates.
 */
@Data
@TableName("interview_verdict")
public class InterviewVerdictPO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The session, not a surrogate: one verdict per interview is the intended constraint. */
    @TableId(type = IdType.INPUT)
    private String sessionId;

    private String candidateId;
    private String jobId;

    private Integer overall;

    /** Derived from {@code overall} in code. The model is never asked for it. */
    private String recommendation;

    private String summary;

    /** The four dimension scores with their evidence, as JSON. */
    private String dimensionsJson;

    private Long gradedMs;

    private LocalDateTime createdTime;
}
