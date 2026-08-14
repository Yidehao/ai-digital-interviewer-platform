package org.interviewer.entity.grading;

/**
 * One line of transcript, as the grader sees it.
 *
 * <p>Note what {@code kind} can be: {@code QUESTION} or {@code ANSWER}. Not {@code FOLLOWUP}.
 *
 * <p>That flattening is deliberate and is the subtlest part of the isolation. A grader that can see
 * "the interviewer chose to probe here" is reading the interviewer's judgement about the candidate
 * — it learns which answers were found weak without being told a single score. The interviewer's
 * transcript keeps the distinction, because the follow-up cap and the audit trail need it; the
 * grader's copy does not get it.
 *
 * @param seq     position in the transcript, so ordering effects can be measured
 * @param kind    QUESTION or ANSWER, never FOLLOWUP
 * @param text    what was said
 * @param seconds how long the answer took, which is a signal about fluency rather than content
 */
public record TranscriptTurn(int seq, String kind, String text, long seconds) {

    public static final String QUESTION = "QUESTION";
    public static final String ANSWER = "ANSWER";
}
