package org.interviewer.entity.grading;

import java.util.List;

/**
 * What the system actually produces about a candidate — which is <b>not</b> a decision.
 *
 * <p>A single {@link Verdict} is one sample from a stochastic process, and this system measured how
 * stochastic: <b>3 of 12 candidates got different scores across two identical runs at temperature
 * 0.</b> Temperature 0 makes decoding greedy, not deterministic — batching, kernel scheduling and
 * floating-point non-associativity all move logits slightly, and near a tie that flips a token. For
 * a tool that gates employment, "the same person can get a different outcome" is disqualifying on
 * its own, independent of whether the average is any good.
 *
 * <p>So grading samples the model {@code n} times and takes the <b>median</b> per dimension. The
 * median, not the mean: with three samples it is robust to a single outlier and it stays on the
 * integer scale the rubric defines, where a mean of 2.67 is a number the rubric has no meaning for.
 *
 * <p><b>Disagreement is the signal, not the noise.</b> When samples of the same transcript disagree,
 * that transcript is near a boundary the model cannot resolve — precisely the case where a human
 * should look. {@code needsHumanReview} carries that forward, and the spread is stored so the rate
 * can be audited rather than guessed at.
 *
 * <p><b>{@code advisory} is always true and there is no code path that sets it false.</b> That is
 * deliberate and it is the honest conclusion of this project's own measurements: a grader with a
 * measured surface-phrasing penalty that <em>scales with competence</em> — worst for the strongest
 * non-native candidates, which is the worst possible shape near a cutoff — is not something that
 * should reject anyone by itself. The field exists so that any future caller which tries to gate on
 * a verdict has to read a flag that says, in the type system, that it must not.
 *
 * @param verdict         the median verdict across samples
 * @param samples         how many times the transcript was graded
 * @param maxDimensionSpread largest gap between the highest and lowest score any single dimension
 *                        received across samples. 0 means every run agreed
 * @param overallSpread   the same for the overall score
 * @param needsHumanReview whether the samples disagreed enough to require a person
 * @param reviewReason    why, in a sentence a recruiter can read
 * @param advisory        always true — see above
 */
public record GradingOutcome(Verdict verdict,
                             int samples,
                             int maxDimensionSpread,
                             int overallSpread,
                             boolean needsHumanReview,
                             String reviewReason,
                             boolean advisory) {

    /**
     * Any disagreement at all escalates.
     *
     * <p>A tempting alternative is to escalate only when the spread is 2 or more, on the grounds
     * that one point is within tolerance. But the measured instability was exactly one point, on
     * 25% of candidates — so a threshold of 2 would escalate almost nothing and would silently
     * accept the instability that made this necessary.
     */
    public static final int ESCALATE_AT_SPREAD = 1;

    public static GradingOutcome of(Verdict median, int samples, int dimensionSpread,
                                    int overallSpread) {
        boolean unstable = dimensionSpread >= ESCALATE_AT_SPREAD
                || overallSpread >= ESCALATE_AT_SPREAD;
        String reason = unstable
                ? ("Repeated grading of this transcript disagreed by up to "
                   + Math.max(dimensionSpread, overallSpread)
                   + " point(s). A human should review it.")
                : "Repeated grading agreed. A human should still review before any decision.";
        return new GradingOutcome(median, samples, dimensionSpread, overallSpread,
                unstable, reason, true);
    }

    /** Convenience for the many callers that only want the scores. */
    public List<DimensionScore> dimensions() {
        return verdict == null ? List.of() : verdict.dimensions();
    }
}
