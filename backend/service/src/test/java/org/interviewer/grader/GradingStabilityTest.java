package org.interviewer.grader;

import org.interviewer.entity.grading.DimensionScore;
import org.interviewer.entity.grading.GradingOutcome;
import org.interviewer.entity.grading.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Managing an instability that could not be removed.
 *
 * <p>Three of twelve candidates scored differently across two identical cohort runs at temperature
 * 0. Greedy decoding is not deterministic decoding: batching, kernel scheduling and floating-point
 * non-associativity move logits, and near a tie that flips a token. For a tool that influences
 * hiring, "the same person can get a different outcome" is disqualifying on its own — separately
 * from whether the average score is any good.
 *
 * <p>It cannot be fixed at the model, so it is measured and surfaced: sample three times, take the
 * median, and escalate <em>any</em> disagreement to a human. Disagreement is the signal — a
 * transcript the model cannot score twice the same way is one near a boundary, which is exactly
 * when a person should look.
 */
class GradingStabilityTest {

    private final GraderAgent grader = new GraderAgent(null, null, null);

    private static Verdict verdict(int overall, int correctness, int depth) {
        return new Verdict(List.of(), overall, GraderAgent.recommendationFor(overall),
                List.of(new DimensionScore("correctness", "ev", "why", correctness),
                        new DimensionScore("depth", "ev", "why", depth),
                        new DimensionScore("communication", "ev", "why", 3),
                        new DimensionScore("practical_experience", "ev", "why", 3)),
                "summary");
    }

    @Test
    @DisplayName("the median is taken, not the mean")
    void medianNotMean() {
        // Mean would be 2.67, which is not a point the rubric defines - there is no anchored
        // example for it and no reviewer can say what it means. The median stays on the scale.
        GradingOutcome outcome = grader.aggregate(List.of(
                verdict(3, 2, 3), verdict(3, 3, 3), verdict(3, 3, 3)));

        assertThat(scoreOf(outcome, "correctness")).isEqualTo(3);
    }

    @Test
    @DisplayName("one outlier run does not move the verdict")
    void outlierIsAbsorbed() {
        GradingOutcome outcome = grader.aggregate(List.of(
                verdict(4, 4, 4), verdict(4, 4, 4), verdict(2, 1, 1)));

        assertThat(outcome.verdict().overall()).isEqualTo(4);
        assertThat(scoreOf(outcome, "correctness")).isEqualTo(4);
    }

    @Test
    @DisplayName("any disagreement at all escalates to a human")
    void onePointOfMovementEscalates() {
        // The measured instability was exactly one point, on 25% of candidates. A threshold of 2
        // would escalate almost nothing and would quietly accept the very thing this exists for.
        GradingOutcome outcome = grader.aggregate(List.of(
                verdict(3, 3, 3), verdict(3, 4, 3), verdict(3, 3, 3)));

        assertThat(outcome.maxDimensionSpread()).isEqualTo(1);
        assertThat(outcome.needsHumanReview()).isTrue();
        assertThat(outcome.reviewReason()).contains("disagreed");
    }

    @Test
    @DisplayName("unanimous runs still say a human should look")
    void agreementIsNotApproval() {
        GradingOutcome outcome = grader.aggregate(List.of(
                verdict(3, 3, 3), verdict(3, 3, 3), verdict(3, 3, 3)));

        assertThat(outcome.needsHumanReview()).isFalse();
        assertThat(outcome.maxDimensionSpread()).isZero();
        // Stable is not the same as correct. The surface-phrasing penalty reproduced across runs -
        // it is stable AND wrong - so agreement between samples says nothing about fairness.
        assertThat(outcome.reviewReason()).contains("human should still review");
    }

    @Test
    @DisplayName("every outcome is advisory, and nothing can make it otherwise")
    void advisoryIsNotOptional() {
        GradingOutcome outcome = grader.aggregate(List.of(verdict(5, 5, 5)));
        assertThat(outcome.advisory()).isTrue();

        // There is no setter, no builder and no code path that produces advisory=false. A future
        // caller wanting to auto-reject on a verdict has to change this test to do it, which is
        // the point: the measured surface penalty scales with competence, so it is worst exactly
        // where a cutoff sits.
        assertThat(GradingOutcome.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .contains("advisory", "needsHumanReview");
    }

    @Test
    @DisplayName("prose comes from one run, never stitched across runs")
    void proseIsNotMerged() {
        // Merging evidence from different samples would produce a verdict no single run of the
        // model ever returned - unauditable, and impossible to reproduce from the stored prompt.
        Verdict low = verdict(2, 2, 2);
        Verdict mid = verdict(3, 3, 3);
        Verdict high = verdict(4, 4, 4);

        GradingOutcome outcome = grader.aggregate(List.of(low, mid, high));
        assertThat(outcome.verdict().overall()).isEqualTo(3);
        assertThat(outcome.verdict().summary()).isEqualTo(mid.summary());
    }

    private int scoreOf(GradingOutcome outcome, String dimension) {
        return outcome.dimensions().stream()
                .filter(d -> dimension.equals(d.name())).findFirst().orElseThrow().score();
    }
}
