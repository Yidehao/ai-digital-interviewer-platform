package org.interviewer.entity.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One line of the interview transcript, and <b>the grader's only input</b>.
 *
 * <p>Note what is not here: no working scores, no evidence, no tool names, no model output that
 * was not spoken. The grader receives a list of these and nothing else, so there is no filtering
 * step that could be got wrong later.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Turn {

    /** Monotonic within a session, from 0. Evidence quotes point at this. */
    private int seq;

    private TurnKind kind;

    /** The bank question this turn belongs to. Null for the closing message. */
    private String questionId;

    private String text;

    private Instant startedAt;

    private Instant endedAt;

    /**
     * Speech-to-text confidence for {@code ANSWER} turns, null otherwise.
     *
     * <p>Captured but not yet acted on. Phase 4 either defines a policy - flag low-confidence
     * turns to the grader, or re-ask below a threshold - or deletes the field. A number nobody
     * reads is worse than no number, because it looks like a control that exists.
     */
    private Double sttConfidence;

    public long durationSeconds() {
        return startedAt == null || endedAt == null
                ? 0L
                : Math.max(0L, endedAt.getEpochSecond() - startedAt.getEpochSecond());
    }
}
