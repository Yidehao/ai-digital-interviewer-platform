package org.interviewer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * The durable record of one agent-loop run.
 *
 * <p>Deliberately a different type from {@code InterviewSession}, which is the hot Redis-backed
 * object the loop mutates every turn. Merging them would leak MyBatis annotations into agent state
 * and let {@code update-strategy: not_empty} silently skip fields that are legitimately zero or
 * empty - which for counters is exactly the value you most want written.
 *
 * <p>{@code @TableName} is explicit rather than inferred: the inference would give
 * {@code interview_session_p_o} for this class name.
 */
@Data
@TableName("interview_session")
public class InterviewSessionPO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String candidateId;
    private String jobId;
    private String interviewerId;
    private String state;

    /** Null is the healthy case: the model chose to finish rather than the loop forcing it. */
    private String terminalReason;

    private String closingMessage;
    private Integer turnCount;
    private Integer toolCallCount;
    private Integer errorCount;

    /**
     * Summed {@code prompt_eval_count} across the session. Prompt evaluation is the dominant term
     * in first-token latency, so watching this grow is how we find out whether the sliding window
     * is doing its job.
     */
    private Integer promptTokens;

    private Integer completionTokens;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
