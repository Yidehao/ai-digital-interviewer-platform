package org.interviewer.entity.grading;

/**
 * One checkable technical assertion, extracted before anything is scored.
 *
 * <p><b>This exists to attack a measured bias, not to add detail.</b> The cohort showed a
 * designed-strong candidate using non-native phrasing losing a point on <em>correctness</em> — a
 * dimension that should be indifferent to phrasing, because a claim about cache invalidation is
 * true or false regardless of the grammar around it. A grader reading correctness straight off the
 * transcript is scoring the claims and the prose together, because they arrive together.
 *
 * <p>Separating them is the same lever as evidence → reasoning → score, which already works:
 * constrained decoding emits fields in schema order, so putting {@code claims} first forces the
 * model to restate the technical content in its own words <em>before</em> a score exists to
 * rationalise towards. Restated rather than quoted, deliberately — a quote carries the phrasing
 * back in.
 *
 * <p>Whether it actually reduces the penalty is a measurement, and the honest expectation is
 * partial: fluency judgement is entangled with everything else in a language model. The residual
 * is what matters and the residual is what gets reported.
 *
 * @param claim  the assertion, restated plainly
 * @param status correct, incorrect, or unverifiable — the last for claims about the candidate's own
 *               history, which cannot be checked and must not count either way
 */
public record Claim(String claim, String status) {

    public static final String CORRECT = "correct";
    public static final String INCORRECT = "incorrect";
    public static final String UNVERIFIABLE = "unverifiable";
}
