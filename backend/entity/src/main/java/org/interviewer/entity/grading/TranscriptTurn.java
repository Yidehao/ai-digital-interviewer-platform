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
 * <p><b>On {@code uncertainTranscription}, and its direction.</b> This flag exists to
 * <em>protect</em> the candidate, not to discount them, and the difference is the whole design.
 *
 * <p>Telling a grader "weight this turn less" would have been the obvious reading of a low
 * confidence score, and it would have been actively harmful here. ASR confidence drops on accented
 * speech, and the cohort already showed this grader penalising non-native phrasing by a point on
 * all four dimensions at the strong tier. Discounting low-confidence turns would therefore have
 * concentrated the penalty on exactly the candidates already being penalised — the measured bias
 * would have been amplified by a feature added to reduce it.
 *
 * <p>So the flag says the opposite: the words may be wrong, judge the ideas. Whether that helps is
 * a measurement, not a claim — {@code SurfaceVariantRunner} is how it gets tested.
 *
 * @param seq     position in the transcript, so ordering effects can be measured
 * @param kind    QUESTION or ANSWER, never FOLLOWUP
 * @param text    what was said
 * @param seconds how long the answer took, which is a signal about fluency rather than content
 * @param uncertainTranscription whether the ASR was unsure of the words in this turn
 */
public record TranscriptTurn(int seq, String kind, String text, long seconds,
                             boolean uncertainTranscription) {

    public static final String QUESTION = "QUESTION";
    public static final String ANSWER = "ANSWER";

    /** Authored text — no ASR involved, so transcription is certain by construction. */
    public TranscriptTurn(int seq, String kind, String text, long seconds) {
        this(seq, kind, text, seconds, false);
    }
}
