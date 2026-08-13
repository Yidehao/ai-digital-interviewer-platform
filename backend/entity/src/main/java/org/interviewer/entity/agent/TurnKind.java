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

    /** A model-generated probe about the previous answer. */
    FOLLOWUP,

    /** Transcribed candidate speech. Untrusted input; always delimited in prompts. */
    ANSWER,

    /** The closing message. Ends the transcript. */
    CLOSING
}
