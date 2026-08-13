package org.interviewer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * One transcript line, persisted.
 *
 * <p>This table is the grader's input. It holds nothing the interviewer thought - no working
 * scores, no evidence, no tool names - because the isolation claim is that such fields have
 * nowhere to live rather than that they are filtered out.
 */
@Data
@TableName("interview_turn")
public class InterviewTurnPO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String sessionId;
    private Integer seq;

    /** QUESTION, FOLLOWUP, ANSWER, CLOSING. See TurnKind on why FOLLOWUP must not reach the grader. */
    private String kind;

    private String questionId;
    private String text;

    /** ANSWER turns only. Captured, not yet acted on; Phase 4 defines a policy or drops it. */
    private Double sttConfidence;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdTime;
}
