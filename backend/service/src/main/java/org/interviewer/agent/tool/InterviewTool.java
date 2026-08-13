package org.interviewer.agent.tool;

/**
 * One tool the interviewer agent can call.
 *
 * <p>Implementations are Spring beans; {@code ToolRegistry} collects them. They arrive in Phase 3 —
 * Phase 1 defines the contract so the schemas, the registry and the type-drift tests have something
 * to be checked against.
 *
 * <p>Three things are deliberately <em>not</em> the tool's job:
 * <ul>
 *   <li><b>Validating its own arguments.</b> The loop validates against
 *       {@code tools/{name}.json} before dispatch, so an implementation may assume its args are
 *       schema-valid. Invalid args become a repair turn, which a tool cannot produce.</li>
 *   <li><b>Enforcing its own timeout.</b> The loop enforces {@link #timeoutMs()} by running the
 *       call on an executor and bounding the {@code get}. A tool that timed itself out would leave
 *       the loop blocked on a tool that decided not to return.</li>
 *   <li><b>Deciding when the interview ends.</b> Only {@link #terminal()} marks a tool as ending
 *       the loop, and exactly one tool sets it. Termination is an explicit action rather than the
 *       absence of a tool call — that is what makes "no tool call emitted" unambiguously mean
 *       something went wrong.</li>
 * </ul>
 *
 * @param <A> the arguments record, matching {@code tools/{name}.json}
 * @param <R> the result record, matching {@code tools/{name}.result.json}
 */
public interface InterviewTool<A, R> {

    /** Which tool this is. Its schema files are derived from this. */
    ToolName name();

    /** The arguments record. Used by the loop to deserialize, and by the drift tests. */
    Class<A> argsType();

    /** The result record. Validated against the result schema after every call. */
    Class<R> resultType();

    /**
     * Run the tool. May assume {@code args} is schema-valid.
     *
     * <p>Throwing is allowed and lands on rung 4 of the fallback ladder as a structured error. It
     * should be reserved for genuine faults: an outcome the model can reason about — an empty
     * question bank, a rejected quote, code that timed out — belongs in the result shape instead.
     */
    R execute(A args, ToolContext ctx);

    /**
     * Wall-clock budget for one call, enforced by the loop. The default suits the four tools that
     * only touch session state; {@code run_code} needs more because a container cold start alone
     * is 200-600 ms.
     */
    default long timeoutMs() {
        return 3_000L;
    }

    /** True only for {@code finish_interview}: this call ends the loop. */
    default boolean terminal() {
        return false;
    }
}
