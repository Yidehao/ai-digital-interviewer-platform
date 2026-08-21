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
     * <p>Read by {@code GradingInputFactory}, which turns it into a flag telling the grader to
     * judge the content of that turn rather than its wording. The direction is deliberate and is
     * documented on {@code TranscriptTurn}: discounting low-confidence turns would have aimed the
     * penalty at accented speech, which is the bias the cohort already measured.
     *
     * <p>Null means the client sent no confidence, which is not the same as a low one.
     */
    private Double sttConfidence;

    /**
     * The avatar clip for this question, when the bank has one.
     *
     * <p>Here rather than looked up again at delivery time because the polling transport has no
     * event to carry it: SSE hands the client {@code aiSrc} alongside the question text, and a
     * client that polls session state must be able to find the same thing in the same place.
     *
     * <p>Not part of the transcript the grader reads — {@code GradingInputFactory} copies text,
     * kind, position and duration, and nothing else.
     */
    private String aiSrc;

    public long durationSeconds() {
        return startedAt == null || endedAt == null
                ? 0L
                : Math.max(0L, endedAt.getEpochSecond() - startedAt.getEpochSecond());
    }
}
