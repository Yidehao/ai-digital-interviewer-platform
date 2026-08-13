package org.interviewer.agent.tool.impl;

import org.interviewer.agent.tool.InterviewTool;
import org.interviewer.agent.tool.ToolContext;
import org.interviewer.agent.tool.ToolName;
import org.interviewer.agent.tool.dto.AskFollowupArgs;
import org.interviewer.agent.tool.dto.AskFollowupResult;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.Turn;
import org.interviewer.entity.agent.TurnKind;
import org.interviewer.utils.AgentProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Deliver a model-generated probe about the answer just given.
 *
 * <p>This is the tool that makes the interview adaptive, and the one worth capping. Two per
 * question and six per session: without a cap a model that finds an answer interesting will keep
 * probing it and never move on, and the candidate experiences an interrogation about one topic
 * rather than an interview.
 *
 * <p>Hitting a cap is {@code delivered:false} with a reason, not an exception. The model can act
 * on a refusal - it fetches the next question instead. It cannot act on a stack trace.
 *
 * <p>Text only, no avatar clip: there is no pre-rendered video for a question that did not exist
 * until a moment ago, so the client renders the text over a neutral idle loop.
 */
@Component
public class AskFollowupTool implements InterviewTool<AskFollowupArgs, AskFollowupResult> {

    private final AgentProperties properties;
    private final Clock clock;

    public AskFollowupTool(AgentProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public ToolName name() {
        return ToolName.ASK_FOLLOWUP;
    }

    @Override
    public Class<AskFollowupArgs> argsType() {
        return AskFollowupArgs.class;
    }

    @Override
    public Class<AskFollowupResult> resultType() {
        return AskFollowupResult.class;
    }

    @Override
    public AskFollowupResult execute(AskFollowupArgs args, ToolContext ctx) {
        InterviewSession session = ctx.session();
        String parent = args.parentQuestionId();

        if (!session.getServedQuestionIds().contains(parent)) {
            // Following up on a question that was never asked means the model has lost the thread.
            // Naming that is more useful than silently attaching the probe to nothing.
            return new AskFollowupResult(false, null, args.question(),
                    session.followupCount(parent), "unknown_parent");
        }
        if (session.followupCount(parent) >= properties.getMaxFollowupsPerQuestion()) {
            return new AskFollowupResult(false, null, args.question(),
                    session.followupCount(parent), "question_cap");
        }
        if (session.totalFollowups() >= properties.getMaxFollowupsPerSession()) {
            return new AskFollowupResult(false, null, args.question(),
                    session.followupCount(parent), "session_cap");
        }

        Turn turn = session.addTurn(TurnKind.FOLLOWUP, parent, args.question(), clock.instant());
        session.getFollowupCounts().merge(parent, 1, Integer::sum);
        ctx.events().onFollowup(turn);

        return new AskFollowupResult(true, String.valueOf(turn.getSeq()), args.question(),
                session.followupCount(parent), null);
    }
}
