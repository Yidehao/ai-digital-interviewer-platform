package org.interviewer.agent.tool.impl;

import org.interviewer.agent.sandbox.CodeRunner;
import org.interviewer.agent.tool.InterviewTool;
import org.interviewer.agent.tool.ToolContext;
import org.interviewer.agent.tool.ToolName;
import org.interviewer.agent.tool.dto.RunCodeArgs;
import org.interviewer.agent.tool.dto.RunCodeResult;
import org.springframework.stereotype.Component;

/**
 * Execute a snippet the candidate dictated, in a sandbox.
 *
 * <p>The schema carries a negative precondition - do not call this for a verbal description of an
 * approach - because a positive description alone was not enough. In the Phase 0.5 benchmark the
 * 3B model reached for this tool in six situations containing no code at all; naming what the tool
 * is *not* for did more work than describing what it is for.
 *
 * <p>The timeout is deliberately generous relative to the other tools: container cold start alone
 * is 200-600 ms before any candidate code runs. That is also why {@code run_code} must never be on
 * the path whose latency gets quoted.
 */
@Component
public class RunCodeTool implements InterviewTool<RunCodeArgs, RunCodeResult> {

    private final CodeRunner runner;

    public RunCodeTool(CodeRunner runner) {
        this.runner = runner;
    }

    @Override
    public ToolName name() {
        return ToolName.RUN_CODE;
    }

    @Override
    public Class<RunCodeArgs> argsType() {
        return RunCodeArgs.class;
    }

    @Override
    public Class<RunCodeResult> resultType() {
        return RunCodeResult.class;
    }

    @Override
    public long timeoutMs() {
        // The loop's budget must exceed the sandbox's own, or the loop would abandon a container
        // that was about to return a perfectly good timeout result.
        return 10_000L;
    }

    @Override
    public RunCodeResult execute(RunCodeArgs args, ToolContext ctx) {
        return runner.run(args.language(), args.source(), args.stdin(), args.timeoutMs());
    }
}
