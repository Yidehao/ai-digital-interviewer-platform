package org.interviewer.entity.grading;

import java.util.List;

/**
 * Everything the grader is given. <b>The entire grader-isolation claim is the shape of this
 * record.</b>
 *
 * <p>If the agent that ran the interview also graded it, it would be grading its own reasoning: it
 * knows which answers it found thin, which follow-ups it chose to spend, what provisional scores it
 * wrote down. Those inflate the final grade in a way no amount of prompt instruction removes. That
 * is planning-context leakage, and the fix is structural rather than procedural.
 *
 * <table>
 *   <tr><th>The grader may see</th><th>The grader may not see</th></tr>
 *   <tr><td>Question text, undifferentiated</td><td>{@code session.messages} — the planning conversation</td></tr>
 *   <tr><td>Answer transcripts and timings</td><td>{@code session.workingScores} — provisional scores</td></tr>
 *   <tr><td>The job rubric</td><td>{@code session.evidence} — the interviewer's chosen quotes</td></tr>
 *   <tr><td>Reference answers, when A/B'd in</td><td>Any tool call, result, or fallback event</td></tr>
 *   <tr><td>Total duration</td><td>{@code terminalReason}, {@code errorCount}, whether it degraded</td></tr>
 * </table>
 *
 * <p><b>No field here is capable of carrying anything in the right column</b>, and
 * {@code GradingInputIsolationTest} reflects over the transitive field types to prove it. That test
 * looks pedantic until someone adds {@code InterviewSession session} "just for logging" in eight
 * months — which is exactly how this kind of leak actually happens, and why the check is a build
 * failure rather than a code review convention.
 *
 * @param sessionId        correlation only. Never appears in the prompt.
 * @param jobName          the role being interviewed for
 * @param rubric           {@code job.grader_prompt}, falling back to {@code job.prompt}
 * @param turns            the transcript, and the only thing said in the interview that gets through
 * @param totalSeconds     interview duration
 * @param referenceAnswers optional, and A/B'd: whether the grader sees model answers changes its
 *                         scores, so it is a variable to measure rather than a default to assume
 */
public record GradingInput(String sessionId,
                           String jobName,
                           String rubric,
                           List<TranscriptTurn> turns,
                           long totalSeconds,
                           List<String> referenceAnswers) {
}
