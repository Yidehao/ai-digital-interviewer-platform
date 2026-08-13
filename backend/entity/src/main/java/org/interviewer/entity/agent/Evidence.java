package org.interviewer.entity.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A quote from the candidate, attached to a competency by {@code record_evidence}.
 *
 * <p>Like {@link WorkingScore}, this is interviewer-side only and never reaches the grader. It
 * exists for the audit trail: a recruiter asking "why did it probe there?" gets an answer, and a
 * quote that matched no answer turn was rejected before it got here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Evidence {

    private String evidenceId;

    private String competency;

    private String quote;

    private String judgment;

    private String questionId;

    /** The ANSWER turn this quote was matched against. */
    private Integer matchedTurnSeq;
}
