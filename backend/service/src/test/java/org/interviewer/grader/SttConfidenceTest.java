package org.interviewer.grader;

import org.interviewer.entity.Job;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.Turn;
import org.interviewer.entity.agent.TurnKind;
import org.interviewer.entity.grading.GradingInput;
import org.interviewer.entity.grading.TranscriptTurn;
import org.interviewer.service.QuestionLibService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The transcription-uncertainty flag, and the direction it points.
 *
 * <p>The direction is the whole reason these tests exist. "Low ASR confidence" has an obvious
 * reading — trust this answer less — and acting on that obvious reading would have made this system
 * measurably more biased, not less: recognisers are least confident on accented speech, and the
 * cohort already showed this grader docking non-native phrasing a point on all four dimensions at
 * the strong tier. Discounting low-confidence turns would have aimed the penalty at the candidates
 * already carrying one.
 *
 * <p>So {@link #promptTellsTheGraderToProtectNotDiscount()} asserts on wording, which is unusual and
 * deliberate. If someone later rewrites that instruction into "weight this turn less", every other
 * test here still passes and the feature quietly inverts.
 */
class SttConfidenceTest {

    private final GradingInputFactory factory =
            new GradingInputFactory(mock(QuestionLibService.class));

    private InterviewSession sessionWithAnswerConfidence(Double confidence) {
        InterviewSession session = new InterviewSession();
        session.addTurn(TurnKind.QUESTION, "q-1", "How would you cache this?", Instant.now());
        Turn answer = session.addTurn(TurnKind.ANSWER, "q-1",
                "I put Reedus in front of the read path.", Instant.now());
        answer.setSttConfidence(confidence);
        return session;
    }

    private List<TranscriptTurn> answersOf(InterviewSession session) {
        return factory.from(session, new Job(), false).turns().stream()
                .filter(t -> TranscriptTurn.ANSWER.equals(t.kind()))
                .toList();
    }

    @Test
    @DisplayName("below the threshold, the answer is marked uncertain")
    void lowConfidenceIsFlagged() {
        assertThat(answersOf(sessionWithAnswerConfidence(0.61)))
                .allMatch(TranscriptTurn::uncertainTranscription);
    }

    @Test
    @DisplayName("above the threshold, it is not")
    void highConfidenceIsNotFlagged() {
        assertThat(answersOf(sessionWithAnswerConfidence(0.97)))
                .noneMatch(TranscriptTurn::uncertainTranscription);
    }

    @Test
    @DisplayName("a missing confidence is not treated as a low one")
    void nullIsNotUncertain() {
        // A client that never sends a confidence - the typed-answer path, and every client built
        // before the STT endpoint returned one - would otherwise have every turn flagged, and a
        // flag that is always on carries no information at all.
        assertThat(answersOf(sessionWithAnswerConfidence(null)))
                .noneMatch(TranscriptTurn::uncertainTranscription);
    }

    @Test
    @DisplayName("authored transcripts are certain by construction")
    void authoredTextIsNeverFlagged() {
        // The cohort and surface runners build TranscriptTurn directly from written text. No
        // recogniser ran, so there is nothing to be uncertain about.
        assertThat(new TranscriptTurn(0, TranscriptTurn.ANSWER, "written by hand", 30)
                .uncertainTranscription()).isFalse();
    }

    @Test
    @DisplayName("the prompt tells the grader to protect the candidate, not to discount them")
    void promptTellsTheGraderToProtectNotDiscount() {
        GraderAgent agent = new GraderAgent(null, null, null);
        String prompt = agent.renderTranscript(new GradingInput(
                "s-1", "Backend Engineer", "rubric",
                List.of(new TranscriptTurn(0, TranscriptTurn.ANSWER, "Reedus", 12, true)),
                12, List.of()));

        assertThat(prompt).contains("[transcription uncertain]");
        assertThat(prompt).contains("Judge the technical content");
        assertThat(prompt).contains("Do not lower a score for phrasing");
        // The inversion this feature must never acquire.
        assertThat(prompt).doesNotContain("weight it less").doesNotContain("weight this turn less");
    }

    @Test
    @DisplayName("with nothing flagged, the prompt is byte-identical to before the feature")
    void unflaggedPromptsAreUnchanged() {
        // The stability and A/B runs compare prompts across variants. A standing caveat about
        // transcription would have altered the baseline for the twelve authored cohort transcripts,
        // where no audio was ever involved - changing the thing being measured.
        GraderAgent agent = new GraderAgent(null, null, null);
        String prompt = agent.renderTranscript(new GradingInput(
                "s-1", "Backend Engineer", "rubric",
                List.of(new TranscriptTurn(0, TranscriptTurn.ANSWER, "Redis", 12)),
                12, List.of()));

        assertThat(prompt).doesNotContain("transcription uncertain");
    }
}
