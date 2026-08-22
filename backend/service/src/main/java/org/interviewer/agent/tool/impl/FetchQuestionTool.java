package org.interviewer.agent.tool.impl;

import lombok.extern.slf4j.Slf4j;
import org.interviewer.agent.AgentEvents;
import org.interviewer.agent.tool.InterviewTool;
import org.interviewer.agent.tool.ToolContext;
import org.interviewer.agent.tool.ToolName;
import org.interviewer.agent.tool.dto.FetchQuestionArgs;
import org.interviewer.agent.tool.dto.FetchQuestionResult;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.Turn;
import org.interviewer.entity.agent.TurnKind;
import org.interviewer.entity.vo.InitQuestionsVO;
import org.interviewer.service.QuestionLibService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Serve the next scripted question from the job's bank.
 *
 * <p>The important thing this tool does is <b>not</b> return {@code referenceAnswer}. A model
 * holding the model answer paraphrases it into its follow-ups, and the candidate is then being
 * assessed on a question that has already been half-answered for them. The grader still sees
 * reference answers, resolved server-side at grading time; the interviewer never does. That
 * asymmetry is enforced by {@link InitQuestionsVO} simply not having the field.
 *
 * <p>An exhausted bank is {@code exhausted:true} with null question fields, never a throw. The
 * model has to be able to reason about "there is nothing left to ask" - it is a normal state of an
 * interview, not a fault.
 */
@Slf4j
@Component
public class FetchQuestionTool implements InterviewTool<FetchQuestionArgs, FetchQuestionResult> {

    private final QuestionLibService questionLibService;
    private final Clock clock;

    public FetchQuestionTool(QuestionLibService questionLibService, Clock clock) {
        this.questionLibService = questionLibService;
        this.clock = clock;
    }

    @Override
    public ToolName name() {
        return ToolName.FETCH_QUESTION;
    }

    @Override
    public Class<FetchQuestionArgs> argsType() {
        return FetchQuestionArgs.class;
    }

    @Override
    public Class<FetchQuestionResult> resultType() {
        return FetchQuestionResult.class;
    }

    @Override
    public long timeoutMs() {
        return 5_000L;
    }

    @Override
    public FetchQuestionResult execute(FetchQuestionArgs args, ToolContext ctx) {
        InterviewSession session = ctx.session();

        // excludeServed defaults to true in the record's constructor, matching the schema. Asking
        // for two at once tells us whether anything remains after this one, without a second query.
        List<String> exclude = Boolean.FALSE.equals(args.excludeServed())
                ? List.of()
                : List.copyOf(session.getServedQuestionIds());

        List<InitQuestionsVO> candidates = questionLibService.getAvailableQuestions(
                session.getInterviewerId(), 2, exclude);

        if (candidates.isEmpty()) {
            return new FetchQuestionResult(null, null, null, null, true, 0);
        }

        InitQuestionsVO picked = candidates.get(0);
        int remaining = candidates.size() - 1;

        session.getServedQuestionIds().add(picked.getId());
        Turn turn = session.addTurn(TurnKind.QUESTION, picked.getId(),
                picked.getQuestion(), clock.instant());

        // Recorded on the turn as well as pushed to the stream. A polling client never sees the
        // event, so session state has to carry everything the question needs to be rendered.
        turn.setAiSrc(picked.getAiSrc());
        ctx.events().onQuestion(turn, picked.getAiSrc());

        // aiSrc goes to the client on the event and on the turn above, and NOT into this result:
        // tool results are quoted back into the conversation, so a video URL here would be re-read
        // by the model on this turn and every turn afterwards, for nothing.
        return new FetchQuestionResult(
                picked.getId(),
                picked.getQuestion(),
                args.topic(),
                args.difficulty(),
                false,
                remaining);
    }
}
