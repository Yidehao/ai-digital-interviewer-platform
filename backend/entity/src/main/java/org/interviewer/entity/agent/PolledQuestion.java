package org.interviewer.entity.agent;

/**
 * What a polling client needs to render the interview, and nothing more.
 *
 * <p>Exists because SSE is not a transport the production client can use everywhere. The uni-app
 * build targets app-plus, mp-weixin and h5, and {@code EventSource} exists only in the last of
 * those. A browser-only agent path would have meant the agent loop stayed unreachable from the
 * actual product, which is the gap Phase 7 exists to close.
 *
 * <p>Derived from session state rather than from a replayed event log. That is the whole point: an
 * event queue alongside the stream would be a second delivery mechanism to keep in step with the
 * first, and the two would drift the first time someone added an event to one path. The pending
 * question is instead <em>computed</em> — the last QUESTION or FOLLOWUP turn with no ANSWER after
 * it — so both transports read the same fact.
 *
 * @param state    session state, so a client that reconnects mid-interview can tell what happened
 * @param turnId   the value to send back with the answer; null when there is nothing to answer
 * @param seq      the turn's position, which the client passes as {@code afterSeq} next time
 * @param question the text to display
 * @param aiSrc    avatar clip URL, already resolved for this deployment, or null
 * @param done     whether the interview has finished, by any route including failure
 */
public record PolledQuestion(String state, String turnId, Integer seq, String question,
                             String aiSrc, boolean done) {

    public static PolledQuestion waiting(String state) {
        return new PolledQuestion(state, null, null, null, null, false);
    }

    public static PolledQuestion finished(String state) {
        return new PolledQuestion(state, null, null, null, null, true);
    }
}
