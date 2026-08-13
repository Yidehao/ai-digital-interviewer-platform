package org.interviewer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * One tool call, persisted.
 *
 * <p>This is the table the "adaptive" claim gets checked against: run two candidates who answer
 * differently and diff their rows. If the sequences are identical, the interview was not adaptive
 * no matter what the architecture diagram says.
 *
 * <p><b>Argument storage is selective, on purpose.</b> Hash-only storage across the board would
 * make production incidents undebuggable - you cannot tell why the model asked for something when
 * all you kept is a checksum. So {@code fetch_question}, {@code score_response} and
 * {@code run_code} store full arguments, none of which contain candidate speech. The two that do -
 * {@code ask_followup.question} and {@code record_evidence.quote} - store only a hash, which is
 * still enough to detect a repeated call.
 */
@Data
@TableName("tool_invocation")
public class ToolInvocationPO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String sessionId;
    private Integer seq;
    private String toolName;

    /** Null for the two tools whose arguments quote the candidate. */
    private String argsJson;

    /** Always present, so repetition is detectable even where the arguments are not stored. */
    private String argsHash;

    /** OK, SCHEMA_REJECTED, ERROR, TIMEOUT. */
    private String outcome;

    /** Which rung of the fallback ladder fired, when one did. */
    private String fallbackReason;

    private Long durationMs;
    private LocalDateTime createdTime;
}
