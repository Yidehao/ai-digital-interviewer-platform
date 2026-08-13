package org.interviewer.agent.tool.impl;

import org.interviewer.agent.tool.InterviewTool;
import org.interviewer.agent.tool.ToolContext;
import org.interviewer.agent.tool.ToolName;
import org.interviewer.agent.tool.dto.FinishInterviewArgs;
import org.interviewer.agent.tool.dto.FinishInterviewResult;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.SessionState;
import org.interviewer.entity.agent.TurnKind;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * End the interview. The only tool with {@link #terminal()} true.
 *
 * <p>Termination being an explicit action is what gives the fallback ladder its meaning: because
 * the model has to <em>say</em> it is finished, "no tool call was emitted" can only mean something
 * went wrong. If stopping were signalled by silence, the two would be indistinguishable and every
 * rung above would be guesswork.
 *
 * <p>Calling it twice returns {@code alreadyFinished:true} rather than failing. A duplicate call
 * at the end of a session is harmless and should stay harmless.
 */
@Component
public class FinishInterviewTool
        implements InterviewTool<FinishInterviewArgs, FinishInterviewResult> {

    private final Clock clock;

    public FinishInterviewTool(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ToolName name() {
        return ToolName.FINISH_INTERVIEW;
    }

    @Override
    public Class<FinishInterviewArgs> argsType() {
        return FinishInterviewArgs.class;
    }

    @Override
    public Class<FinishInterviewResult> resultType() {
        return FinishInterviewResult.class;
    }

    @Override
    public boolean terminal() {
        return true;
    }

    @Override
    public FinishInterviewResult execute(FinishInterviewArgs args, ToolContext ctx) {
        InterviewSession session = ctx.session();
        int asked = session.getServedQuestionIds().size();

        if (session.isTerminal()) {
            return new FinishInterviewResult(true, args.reason(), true, asked, false);
        }

        session.addTurn(TurnKind.CLOSING, null, args.closingMessage(), clock.instant());
        session.setState(SessionState.FINISHED);
        session.setFinishedAt(clock.instant());
        session.setClosingMessage(args.closingMessage());

        ctx.events().onFinished(SessionState.FINISHED, args.closingMessage());

        // Grading is dispatched by the loop's caller once the session is persisted, not from
        // inside a tool: a detached MCP call must never queue a real grading run.
        return new FinishInterviewResult(true, args.reason(), false, asked, !ctx.detached());
    }
}
