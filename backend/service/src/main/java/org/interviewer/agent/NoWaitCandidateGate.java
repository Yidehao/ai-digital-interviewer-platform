package org.interviewer.agent;

import org.interviewer.entity.agent.InterviewSession;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The load-test gate: never waits.
 *
 * <p>Phase 8 asks what the app tier sustains — emitters, three thread pools, tool dispatch, Redis,
 * MySQL — with the model stubbed. There are no candidates in that measurement, so a gate that
 * waited for one would park every session for the full answer timeout and the run would measure
 * {@code Object.wait}.
 *
 * <p>Declaring it here rather than leaving the load test to notice is deliberate: the previous
 * version of this benchmark scored connection rejections as completed sessions and reported 114
 * concurrent when the true figure was 48. A load test that quietly measures the wrong thing is the
 * failure mode this project has already had once.
 *
 * <p><b>The 48-concurrent figure is therefore "app tier, model stubbed, no answer waiting".</b>
 * With real candidates each session spends most of its life parked on a monitor holding no thread,
 * so the binding constraint moves to memory and emitter count rather than the agent pool. That is a
 * different measurement and has not been made.
 */
@Primary
@Profile("loadtest")
@Component
public class NoWaitCandidateGate implements CandidateGate {

    @Override
    public boolean awaitAnswer(InterviewSession session, int questionSeq, long timeoutMs) {
        return true;
    }
}
