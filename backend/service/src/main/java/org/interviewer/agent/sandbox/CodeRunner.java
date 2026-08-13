package org.interviewer.agent.sandbox;

import org.interviewer.agent.tool.dto.RunCodeResult;

/**
 * Runs code the candidate dictated.
 *
 * <p>An interface so the tool can be tested without Docker, and so a different isolation mechanism
 * (gVisor, Firecracker, a remote execution service) could replace the container without touching
 * anything above it.
 */
public interface CodeRunner {

    /** True when the runner can actually execute — images pulled, daemon reachable. */
    boolean isAvailable();

    /**
     * Execute and return the same six fields whatever happens.
     *
     * <p>Never throws for anything the code did. Code that crashes, loops forever, or prints a
     * gigabyte is <em>data about the candidate</em>, and the model has to be able to reason about
     * it as an interview event rather than a system fault.
     */
    RunCodeResult run(String language, String source, String stdin, long timeoutMs);
}
