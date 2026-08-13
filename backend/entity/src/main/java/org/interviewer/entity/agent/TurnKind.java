package org.interviewer.entity.agent;

/**
 * What kind of thing a transcript turn is.
 *
 * <p>{@code ANSWER} turns are the only ones {@code record_evidence} may quote from - that is the
 * anti-fabrication guard, and it only works because the transcript distinguishes what the
 * candidate said from what the interviewer said.
 */
public enum TurnKind {

    /** A scripted question from the bank. */
    QUESTION,

    /**
     * A model-generated probe about the previous answer.
     *
     * <p><b>This distinction must not survive into {@code GradingInput}.</b> A grader that can see
     * "the interviewer chose to probe here" is reading the interviewer's judgement about the
     * candidate, which is exactly the planning-context leakage the separate grader exists to
     * prevent — it would learn which answers the interviewer found weak without being told a
     * single score.
     *
     * <p>Phase 4's {@code TranscriptTurn} mapping collapses FOLLOWUP to QUESTION. Keeping the kind
     * here is right: the interviewer needs it for the per-question follow-up cap, and the audit
     * trail needs it to answer "why did it probe there?".
     */
    FOLLOWUP,

    /** Transcribed candidate speech. Untrusted input; always delimited in prompts. */
    ANSWER,

    /** The closing message. Ends the transcript. */
    CLOSING
}
