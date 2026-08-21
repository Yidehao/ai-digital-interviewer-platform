package org.interviewer.agent;

import lombok.extern.slf4j.Slf4j;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.Turn;
import org.interviewer.entity.agent.TurnKind;
import org.springframework.stereotype.Component;

/**
 * Waits on the session's own monitor, which is the lock answers are already appended under.
 *
 * <p>Polling {@code getTurns()} on a timer would have worked and would have burned a thread per
 * live interview doing nothing. {@code submitAnswer} already synchronises on the session to append
 * the turn, so the notification costs one {@code notifyAll} on a lock that is being taken anyway.
 *
 * <p>The wait is re-checked in a loop rather than trusted once: {@code Object.wait} may return
 * spuriously, and treating that as "the candidate answered" would ask the next question into
 * silence — the exact failure this class exists to prevent.
 */
@Slf4j
@Component
public class MonitorCandidateGate implements CandidateGate {

    @Override
    public boolean awaitAnswer(InterviewSession session, int questionSeq, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (session) {
            while (!answered(session, questionSeq)) {
                if (session.isTerminal()) {
                    return false;
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    log.info("session {} waited {} ms for an answer to turn {} and gave up",
                            session.getSessionId(), timeoutMs, questionSeq);
                    return false;
                }
                try {
                    session.wait(remaining);
                } catch (InterruptedException e) {
                    // Shutdown. Restore the flag and let the loop close the interview cleanly
                    // rather than swallowing the interrupt and waiting again.
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    /** Any ANSWER turn after the question is an answer to it: answers are appended in order. */
    private boolean answered(InterviewSession session, int questionSeq) {
        for (Turn turn : session.getTurns()) {
            if (turn.getKind() == TurnKind.ANSWER && turn.getSeq() > questionSeq) {
                return true;
            }
        }
        return false;
    }
}
