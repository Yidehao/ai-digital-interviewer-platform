package org.interviewer.agent.tool.impl;

import org.interviewer.agent.tool.InterviewTool;
import org.interviewer.agent.tool.ToolContext;
import org.interviewer.agent.tool.ToolName;
import org.interviewer.agent.tool.dto.ScoreResponseArgs;
import org.interviewer.agent.tool.dto.ScoreResponseResult;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.WorkingScore;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Record the interviewer's private working score for one competency.
 *
 * <p><b>This is not the grade.</b> It steers the interviewer's own planning - what is covered,
 * what still needs probing - and it never reaches the grader, is never persisted as a score, and
 * is never shown to the candidate. If it did reach the grader, the grader would stop being an
 * independent second opinion and start agreeing with a number it was handed.
 *
 * <p>Idempotent on {@code (questionId, dimension)}. {@code supersededPrevious} is what stops a
 * model re-scoring the same pair in a loop: it is told the score already existed, which is a
 * different signal from the call having failed.
 */
@Component
public class ScoreResponseTool implements InterviewTool<ScoreResponseArgs, ScoreResponseResult> {

    @Override
    public ToolName name() {
        return ToolName.SCORE_RESPONSE;
    }

    @Override
    public Class<ScoreResponseArgs> argsType() {
        return ScoreResponseArgs.class;
    }

    @Override
    public Class<ScoreResponseResult> resultType() {
        return ScoreResponseResult.class;
    }

    @Override
    public ScoreResponseResult execute(ScoreResponseArgs args, ToolContext ctx) {
        InterviewSession session = ctx.session();

        WorkingScore score = new WorkingScore(args.questionId(), args.dimension(),
                args.score(), args.evidence(), args.confidence());
        WorkingScore previous = session.getWorkingScores().put(score.key(), score);

        Set<String> dimensions = new HashSet<>();
        session.getWorkingScores().values().forEach(s -> dimensions.add(s.getDimension()));

        return new ScoreResponseResult(true, args.questionId(), args.dimension(), args.score(),
                previous != null, dimensions.size());
    }
}
