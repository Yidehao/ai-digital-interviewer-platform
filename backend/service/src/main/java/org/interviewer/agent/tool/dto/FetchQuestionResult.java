package org.interviewer.agent.tool.dto;

/**
 * Result of {@code fetch_question}. Mirrors {@code tools/fetch_question.result.json}.
 *
 * <p>Note what is absent: {@code referenceAnswer}. A model holding the model answer paraphrases it
 * into its follow-ups, and the candidate is then being marked against a question that has already
 * been half-answered for them. The grader still sees reference answers server-side; the
 * interviewer never does.
 *
 * <p>An empty bank is {@code exhausted=true} with null question fields — one shape for every
 * outcome, so the model never has to branch on which keys came back.
 *
 * <p>{@code aiSrc} was here and is deliberately gone. It is an avatar-clip URL for the client, and
 * the interviewer can do precisely nothing with it — but every tool result is quoted back into the
 * conversation, so a ~50-character URL was being re-read on that turn and every turn after it. It
 * reaches the client the way it always should have: on the {@code question} event, and on
 * {@code Turn.aiSrc} for clients that poll.
 */
public record FetchQuestionResult(
        String questionId,
        String question,
        String topic,
        String difficulty,
        boolean exhausted,
        int remaining) {
}
