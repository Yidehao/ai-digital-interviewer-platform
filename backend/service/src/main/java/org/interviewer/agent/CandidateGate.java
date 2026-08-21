package org.interviewer.agent;

import org.interviewer.entity.agent.InterviewSession;

/**
 * Back-pressure from the candidate: the loop must not ask a second question before the first has
 * been answered.
 *
 * <p><b>This exists because it was missing, and its absence did not look like a bug.</b> The loop
 * asked a question, the tool returned, and the loop immediately called the model again — which,
 * seeing a completed tool call and no answer, asked another question. A polling client driving a
 * real interview produced a session with 8 QUESTION turns, 6 FOLLOWUP turns and <b>zero ANSWER
 * turns</b>: fourteen questions asked to nobody in about nine seconds, then a polite closing
 * message.
 *
 * <p>It went unnoticed for so long because every path that exercised the loop hid it. The eval
 * runners feed authored transcripts to the grader and never run the loop at all. The load test
 * stubs the model, where a session with no answers is exactly what is being measured. And the SSE
 * harness <em>appeared</em> to work: answers POSTed successfully and returned {@code success:true},
 * because {@code submitAnswer} does accept them — it appends them to a transcript the loop has
 * already run past. A green check on the request said nothing about whether anyone was listening.
 *
 * <p>A collaborator rather than a {@code sleep} in the loop, because "wait for a human" is exactly
 * the behaviour a headless test must be able to switch off. {@link #NONE} is what the unit tests
 * and the load-test profile use, and it makes their assumption explicit instead of accidental.
 */
public interface CandidateGate {

    /**
     * Block until the candidate has answered the turn at {@code questionSeq}, or give up.
     *
     * @return true if an answer arrived, false on timeout — the caller decides what a timeout means
     */
    boolean awaitAnswer(InterviewSession session, int questionSeq, long timeoutMs);

    /**
     * No waiting at all: the loop runs as fast as the model will let it.
     *
     * <p>Correct for unit tests and for the load test, where the model is stubbed and the thing
     * being measured is the app tier rather than an interview. Wrong for anything with a person on
     * the other end, which is the whole point of naming it.
     */
    CandidateGate NONE = (session, questionSeq, timeoutMs) -> true;
}
