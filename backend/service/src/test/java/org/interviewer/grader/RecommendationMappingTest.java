package org.interviewer.grader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code recommendation} follows from {@code overall}, by construction.
 *
 * <p>This test exists because the previous approach did not work and the failure is worth
 * remembering. {@code recommendation} was in the verdict schema and the model produced it. Across
 * two full twelve-participant runs it agreed with {@code overall} on <b>1 of 12</b> verdicts —
 * candidates scored 2 out of 5 came back {@code strong_yes}. Stating the mapping in the schema's
 * field description changed nothing: both runs returned byte-identical results.
 *
 * <p>Constrained decoding guarantees the <em>shape</em> of the output and nothing about
 * <em>coherence between fields</em>: every enum member satisfies the schema equally well, so there
 * is no pressure toward the one that matches the score. The fix is not a better prompt. It is to
 * stop asking, and compute it — after which a unit test settles it in a millisecond instead of
 * sixteen minutes of GPU time.
 */
class RecommendationMappingTest {

    @ParameterizedTest(name = "overall {0} -> {1}")
    @CsvSource({
            "1, strong_no",
            "2, no",
            "3, borderline",
            "4, yes",
            "5, strong_yes"
    })
    void mapsEveryScore(int overall, String expected) {
        assertThat(GraderAgent.recommendationFor(overall)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a candidate scored below 4 is never recommended for advancement")
    void lowScoresAreNeverAnAdvance() {
        // The exact failure observed: overall 2 returning strong_yes. A recruiter skimming
        // recommendations rather than scores would have advanced a candidate the grader rated
        // 2 out of 5.
        for (int overall = 1; overall <= 3; overall++) {
            assertThat(GraderAgent.recommendationFor(overall))
                    .as("overall %d", overall)
                    .isNotIn("yes", "strong_yes");
        }
    }
}
