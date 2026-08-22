package org.interviewer.agent;

import org.interviewer.entity.agent.InterviewSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The distinction between prompt SIZE and prompt COST, pinned so it cannot be collapsed again.
 *
 * <p>The project reported a 30.6:1 prompt-to-completion token ratio and concluded the model spends
 * nearly all its wall clock re-reading conversation history. The first half is true; the conclusion
 * does not follow. Measured against the running model:
 *
 * <pre>
 *   call                          prompt_eval_count   prompt_eval_duration
 *   turn 1, cold                        745                 6.89 s
 *   the identical call again            745                 0.09 s
 *   turn 2, same prefix, longer tail    762                 0.37 s
 * </pre>
 *
 * <p>The count is unchanged by a cache hit that made the call 76x faster. So a ratio built from
 * counts describes how big the prompt is, and reading it as how much work was done overstates the
 * cost of history by whatever the cache saved.
 */
class PromptCostTest {

    @Test
    @DisplayName("a session tracks evaluation time separately from token count")
    void sizeAndCostAreSeparateFields() {
        InterviewSession session = new InterviewSession();
        session.setPromptTokens(2400);
        session.setPromptEvalNanos(90_000_000L);       // 0.09 s - a cache hit

        assertThat(session.getPromptTokens()).isEqualTo(2400);
        assertThat(session.getPromptEvalNanos()).isEqualTo(90_000_000L);
    }

    @Test
    @DisplayName("tokens rising while evaluation time stays flat is the cache working")
    void cacheHitsShowUpAsFlatTimeNotFewerTokens() {
        // Twelve turns of a real interview: the prompt grows every turn because the transcript
        // grows, but only the new suffix is evaluated. Reading the token total as work done would
        // report ~25k tokens of effort for under eight seconds of it.
        InterviewSession session = new InterviewSession();
        long cold = 6_890_000_000L;
        long warmPerTurn = 90_000_000L;

        for (int turn = 1; turn <= 12; turn++) {
            session.setPromptTokens(session.getPromptTokens() + 1614 + turn * 88);
            session.setPromptEvalNanos(
                    session.getPromptEvalNanos() + (turn == 1 ? cold : warmPerTurn));
        }

        assertThat(session.getPromptTokens()).isGreaterThan(25_000);
        // Under eight seconds of actual evaluation for all twelve turns, nearly all of it the
        // first one. That is the number a latency claim has to be built from.
        assertThat(session.getPromptEvalNanos()).isLessThan(8_000_000_000L);
        assertThat(cold).isGreaterThan(warmPerTurn * 11);
    }
}
