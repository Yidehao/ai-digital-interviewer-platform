package org.interviewer.orchestrator;

import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.PolledQuestion;
import org.interviewer.entity.agent.SessionState;
import org.interviewer.entity.agent.TurnKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Which question is the candidate answering right now?" — computed, never replayed.
 *
 * <p>The polling transport exists because {@code EventSource} is absent from two of the three
 * uni-app targets. The temptation when adding a second transport is to give it its own queue of
 * pending events, and that is the mistake these tests exist to prevent: two delivery mechanisms
 * drift apart the moment anyone adds an event to one of them. The pending question is instead a
 * fact about the transcript, so SSE and polling cannot disagree about it.
 *
 * <p>Reflection is used rather than widening the method to package-private-for-testing, because the
 * behaviour under test is the orchestrator's and moving it somewhere more convenient would move it
 * away from the state it reasons about.
 */
class PendingQuestionTest {

    private static InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setSessionId("s-1");
        session.setState(SessionState.RUNNING);
        return session;
    }

    /** Mirrors {@code InterviewOrchestrator.pendingQuestion}, which is private on purpose. */
    private static Integer pendingSeq(InterviewSession session) throws Exception {
        Method m = InterviewOrchestrator.class
                .getDeclaredMethod("pendingQuestion", InterviewSession.class);
        m.setAccessible(true);
        Object turn = m.invoke(orchestratorStub(), session);
        return turn == null ? null : ((org.interviewer.entity.agent.Turn) turn).getSeq();
    }

    /**
     * The method reads only its argument, so an instance with null collaborators is enough — and
     * is a great deal more honest than mocking nine constructor parameters to exercise a loop.
     */
    private static InterviewOrchestrator orchestratorStub() throws Exception {
        var constructor = InterviewOrchestrator.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] args = new Object[constructor.getParameterCount()];
        return (InterviewOrchestrator) constructor.newInstance(args);
    }

    @Test
    @DisplayName("a question with no answer after it is the pending one")
    void unansweredQuestionIsPending() throws Exception {
        InterviewSession s = session();
        s.addTurn(TurnKind.QUESTION, "q-1", "How would you cache this?", Instant.now());
        assertThat(pendingSeq(s)).isZero();
    }

    @Test
    @DisplayName("once answered, nothing is pending")
    void answeredQuestionIsNotPending() throws Exception {
        InterviewSession s = session();
        s.addTurn(TurnKind.QUESTION, "q-1", "How would you cache this?", Instant.now());
        s.addTurn(TurnKind.ANSWER, "q-1", "I used Redis.", Instant.now());
        assertThat(pendingSeq(s)).isNull();
    }

    @Test
    @DisplayName("a follow-up becomes the pending question")
    void followupIsPending() throws Exception {
        InterviewSession s = session();
        s.addTurn(TurnKind.QUESTION, "q-1", "How would you cache this?", Instant.now());
        s.addTurn(TurnKind.ANSWER, "q-1", "I used Redis.", Instant.now());
        s.addTurn(TurnKind.FOLLOWUP, "q-1", "What did you do about staleness?", Instant.now());
        assertThat(pendingSeq(s)).isEqualTo(2);
    }

    @Test
    @DisplayName("the closing message is not something to answer")
    void closingIsNotPending() throws Exception {
        // Otherwise the candidate is shown "thanks for your time" with a record button under it.
        InterviewSession s = session();
        s.addTurn(TurnKind.QUESTION, "q-1", "How would you cache this?", Instant.now());
        s.addTurn(TurnKind.ANSWER, "q-1", "I used Redis.", Instant.now());
        s.addTurn(TurnKind.CLOSING, null, "Thanks for your time.", Instant.now());
        assertThat(pendingSeq(s)).isNull();
    }

    @Test
    @DisplayName("an interview that has not asked anything yet has nothing pending")
    void emptySessionHasNothingPending() throws Exception {
        assertThat(pendingSeq(session())).isNull();
    }

    @Test
    @DisplayName("the polled payload never tells the candidate a turn was a follow-up")
    void kindIsNotExposed() {
        // The SSE payload omits Turn.kind for this reason and the second transport has to make the
        // same omission: showing "follow-up" tells the candidate the interviewer judged their last
        // answer weak enough to probe, which is the interviewer's opinion of them.
        assertThat(PolledQuestion.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("state", "turnId", "seq", "question", "aiSrc", "done")
                .doesNotContain("kind");
    }
}
