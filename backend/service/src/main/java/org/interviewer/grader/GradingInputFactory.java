package org.interviewer.grader;

import org.interviewer.entity.Job;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.Turn;
import org.interviewer.entity.agent.TurnKind;
import org.interviewer.entity.grading.GradingInput;
import org.interviewer.entity.grading.TranscriptTurn;
import org.interviewer.service.QuestionLibService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The <b>only</b> way a {@link GradingInput} gets built.
 *
 * <p>Being the only constructor is the point. If callers could assemble a {@code GradingInput}
 * themselves, the isolation would depend on every future caller remembering what not to include —
 * and the whole argument for structural isolation is that it does not depend on anyone remembering
 * anything.
 *
 * <p>Two flattenings happen here, both deliberate:
 *
 * <ul>
 *   <li><b>FOLLOWUP becomes QUESTION.</b> A grader that can see where the interviewer chose to
 *       probe is reading the interviewer's judgement about the candidate, which is the leakage this
 *       whole design exists to prevent.</li>
 *   <li><b>CLOSING is dropped.</b> It is the interviewer's canned sign-off. Worse, when the loop
 *       forces a finish the closing message differs by reason — so leaving it in would tell the
 *       grader that the interview hit an error budget, and "this one went badly" is precisely the
 *       kind of prejudicial context being excluded.</li>
 * </ul>
 */
@Component
public class GradingInputFactory {

    private final QuestionLibService questionLibService;

    public GradingInputFactory(QuestionLibService questionLibService) {
        this.questionLibService = questionLibService;
    }

    /**
     * @param withReferenceAnswers whether to include reference answers. A/B'd rather than assumed:
     *                             a grader holding the model answer scores differently, and which
     *                             way is better is a measurement, not a guess.
     */
    public GradingInput from(InterviewSession session, Job job, boolean withReferenceAnswers) {
        List<TranscriptTurn> turns = new ArrayList<>();
        for (Turn turn : session.getTurns()) {
            if (turn.getKind() == TurnKind.CLOSING) {
                continue;
            }
            String kind = turn.getKind() == TurnKind.ANSWER
                    ? TranscriptTurn.ANSWER
                    : TranscriptTurn.QUESTION;
            turns.add(new TranscriptTurn(turns.size(), kind, turn.getText(),
                    turn.durationSeconds()));
        }

        String rubric = job == null ? "" : firstNonBlank(job.getGraderPrompt(), job.getPrompt());

        return new GradingInput(
                session.getSessionId(),
                job == null ? "" : job.getJobName(),
                rubric,
                List.copyOf(turns),
                totalSeconds(session),
                withReferenceAnswers ? referenceAnswers(session) : List.of());
    }

    /**
     * Resolved server-side, from the question ids the session served.
     *
     * <p>The interviewer never received these — {@code fetch_question} deliberately omits them, so a
     * model cannot paraphrase the model answer into its follow-ups. The grader is a different
     * consumer with a different need, and this is where that asymmetry is implemented.
     */
    private List<String> referenceAnswers(InterviewSession session) {
        Set<String> ids = new LinkedHashSet<>(session.getServedQuestionIds());
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, String> byId = questionLibService.getReferenceAnswers(List.copyOf(ids));
        return ids.stream().map(byId::get).filter(a -> a != null && !a.isBlank()).toList();
    }

    /**
     * Derived from the transcript, not from wall-clock.
     *
     * <p>A session that sat idle while Ollama was slow would otherwise look like a candidate who
     * took a long time to answer, and duration is a signal the grader is allowed to read.
     */
    private long totalSeconds(InterviewSession session) {
        return session.getTurns().stream().mapToLong(Turn::durationSeconds).sum();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred
                : (fallback == null ? "" : fallback);
    }
}
