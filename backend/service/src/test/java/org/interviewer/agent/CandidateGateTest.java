package org.interviewer.agent;

import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.SessionState;
import org.interviewer.entity.agent.TurnKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Turn-taking, which the interview did not have.
 *
 * <p>Before this gate existed the loop asked its next question the moment a tool call returned. A
 * real session driven over the polling transport produced <b>8 QUESTION turns, 6 FOLLOWUP turns and
 * 0 ANSWER turns</b> in about nine seconds, then closed politely. Every path that exercised the loop
 * concealed it: the eval runners grade authored transcripts without running the loop, the load test
 * stubs the model and expects answerless sessions, and the SSE harness returned {@code
 * success:true} for answers appended to a transcript the loop had already passed.
 */
class CandidateGateTest {

    private final MonitorCandidateGate gate = new MonitorCandidateGate();

    private static InterviewSession running() {
        InterviewSession session = new InterviewSession();
        session.setSessionId("s-1");
        session.setState(SessionState.RUNNING);
        session.addTurn(TurnKind.QUESTION, "q-1", "How would you cache this?", Instant.now());
        return session;
    }

    @Test
    @DisplayName("an answer that has already arrived does not wait at all")
    void answerAlreadyPresentReturnsImmediately() {
        InterviewSession s = running();
        s.addTurn(TurnKind.ANSWER, "q-1", "I used Redis.", Instant.now());

        long started = System.currentTimeMillis();
        assertThat(gate.awaitAnswer(s, 0, 5_000)).isTrue();
        assertThat(System.currentTimeMillis() - started).isLessThan(1_000);
    }

    @Test
    @DisplayName("waits, then returns as soon as the answer is appended")
    void answerArrivingLaterWakesTheLoop() throws Exception {
        InterviewSession s = running();
        AtomicBoolean woke = new AtomicBoolean(false);

        Thread loop = new Thread(() -> woke.set(gate.awaitAnswer(s, 0, 10_000)));
        loop.start();
        Thread.sleep(150);
        assertThat(loop.isAlive()).as("the loop must actually be waiting").isTrue();

        // Exactly what InterviewOrchestrator.submitAnswer does.
        synchronized (s) {
            s.addTurn(TurnKind.ANSWER, "q-1", "I used Redis.", Instant.now());
            s.notifyAll();
        }

        loop.join(5_000);
        assertThat(woke).isTrue();
    }

    @Test
    @DisplayName("gives up when the candidate never answers")
    void silenceTimesOut() {
        // A person closing their laptop. Not an error, but the session must not hold a slot until
        // the 45-minute wall clock retires it.
        long started = System.currentTimeMillis();
        assertThat(gate.awaitAnswer(running(), 0, 300)).isFalse();
        assertThat(System.currentTimeMillis() - started).isGreaterThanOrEqualTo(300);
    }

    @Test
    @DisplayName("a terminal session stops waiting rather than sitting out the timeout")
    void terminalSessionDoesNotWait() {
        InterviewSession s = running();
        s.setState(SessionState.FINISHED);
        long started = System.currentTimeMillis();
        assertThat(gate.awaitAnswer(s, 0, 10_000)).isFalse();
        assertThat(System.currentTimeMillis() - started).isLessThan(1_000);
    }

    @Test
    @DisplayName("NONE never waits, which is what headless runs depend on")
    void noneIsImmediate() {
        assertThat(CandidateGate.NONE.awaitAnswer(running(), 0, 60_000)).isTrue();
    }
}
