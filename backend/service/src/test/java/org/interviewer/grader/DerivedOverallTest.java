package org.interviewer.grader;

import org.interviewer.entity.grading.DimensionScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code overall} is computed, because asking for it produced a number inconsistent with the
 * model's own scores.
 *
 * <p>It used to sit in the schema <em>before</em> {@code dimensions}. Constrained decoding emits
 * fields in schema order, so the model chose a headline number before scoring a single dimension or
 * writing any evidence — the inversion of the discipline enforced within each dimension — and then
 * rationalised beneath it.
 *
 * <p>Observed on a real interview: the model returned {@code overall=3} while scoring the four
 * dimensions 4, 4, 4, 3. A human reviewer independently produced the same four dimension scores and
 * an overall of 4. They agreed on everything they had actually looked at.
 */
class DerivedOverallTest {

    private static List<DimensionScore> scores(int correctness, int depth,
                                               int communication, int practical) {
        return List.of(new DimensionScore("correctness", "e", "r", correctness),
                new DimensionScore("depth", "e", "r", depth),
                new DimensionScore("communication", "e", "r", communication),
                new DimensionScore("practical_experience", "e", "r", practical));
    }

    @Test
    @DisplayName("the real case: 4,4,4,3 is a 4, not a 3")
    void theCaseThatMotivatedThis() {
        // mean 3.75 -> 4. The model said 3; the human said 4.
        assertThat(GraderAgent.overallFrom(scores(4, 4, 4, 3))).isEqualTo(4);
    }

    @Test
    @DisplayName("the earlier case: all twos cannot produce a three")
    void allTwosIsATwo() {
        // The first live verdict returned overall=3 with every dimension at 2. Same defect,
        // opposite direction - which is what made it a coherence failure rather than a bias.
        assertThat(GraderAgent.overallFrom(scores(2, 2, 2, 2))).isEqualTo(2);
    }

    @Test
    @DisplayName("rounds half up, and stays on the scale")
    void roundingAndBounds() {
        assertThat(GraderAgent.overallFrom(scores(3, 3, 4, 4))).isEqualTo(4);   // 3.5 -> 4
        assertThat(GraderAgent.overallFrom(scores(1, 1, 1, 1))).isEqualTo(1);
        assertThat(GraderAgent.overallFrom(scores(5, 5, 5, 5))).isEqualTo(5);
    }

    @Test
    @DisplayName("overall is always consistent with the dimensions printed beside it")
    void neverContradictsItsOwnDimensions() {
        // The property that failed before: a reader adding up the four scores on screen must
        // arrive at the number next to them. Exhaustive over the whole 5^4 space.
        for (int a = 1; a <= 5; a++) {
            for (int b = 1; b <= 5; b++) {
                for (int c = 1; c <= 5; c++) {
                    for (int d = 1; d <= 5; d++) {
                        int overall = GraderAgent.overallFrom(scores(a, b, c, d));
                        int min = Math.min(Math.min(a, b), Math.min(c, d));
                        int max = Math.max(Math.max(a, b), Math.max(c, d));
                        assertThat(overall)
                                .as("overall for %d,%d,%d,%d must lie within its dimensions",
                                        a, b, c, d)
                                .isBetween(min, max);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("recommendation still follows from the derived overall")
    void recommendationChains() {
        int overall = GraderAgent.overallFrom(scores(4, 4, 4, 3));
        assertThat(GraderAgent.recommendationFor(overall)).isEqualTo("yes");
    }
}
