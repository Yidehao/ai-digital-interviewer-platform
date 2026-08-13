package org.interviewer.utils;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Budgets and limits for the interviewer agent.
 *
 * <p>Every one of these is a bound on a loop driven by a model, which is to say a loop whose exit
 * condition is not under our control. They are checked <em>before</em> any model call, so an
 * exhausted budget never spends a generation.
 */
@Data
@Component
@ConfigurationProperties(prefix = "interviewer.agent")
public class AgentProperties {

    /** Hard ceiling on model round trips. Reaching it forces a polite finish. */
    private int maxTurns = 40;

    private int maxToolCalls = 60;

    /**
     * Errors of any rung before the loop gives up and closes the interview. Six is chosen so a
     * genuinely confused model still delivers a complete interview rather than a stub.
     */
    private int maxToolErrors = 6;

    /**
     * Repairs offered per invalid tool call. One, not more: the Phase 0.5 benchmark showed a
     * single repair fixed 84% of invalid calls, and a second repair fixed none - a model that
     * cannot fix its arguments when handed the validation errors will not fix them on the third
     * try either, it will re-emit the same value.
     */
    private int maxRepairs = 1;

    private int maxWallClockMinutes = 45;

    /** Follow-ups per parent question. */
    private int maxFollowupsPerQuestion = 2;

    /** Follow-ups across the whole session. */
    private int maxFollowupsPerSession = 6;

    /**
     * Transcript turns kept verbatim in the prompt, plus all tool results from the current turn.
     *
     * <p>Without a window, {@code messages} grows unboundedly and prompt evaluation - the dominant
     * term in first-token latency - grows with it. A latency figure measured at turn 2 would not
     * survive to turn 20.
     *
     * <p>Evict in large infrequent steps rather than one turn at a time: every eviction changes the
     * prefix and invalidates the KV cache from that point on, so trimming on every turn would
     * forfeit the 60x that prefix caching is worth.
     */
    private int historyWindow = 3;

    /** Default per-tool timeout, enforced by the loop rather than by the tool. */
    private long defaultToolTimeoutMs = 3_000L;
}
