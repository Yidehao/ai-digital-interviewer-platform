package org.interviewer.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.interviewer.entity.InterviewVerdictPO;
import org.interviewer.entity.Job;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.grading.GradingInput;
import org.interviewer.entity.grading.Verdict;
import org.interviewer.grader.GraderAgent;
import org.interviewer.grader.GradingInputFactory;
import org.interviewer.mapper.InterviewVerdictMapper;
import org.interviewer.service.JobService;
import org.interviewer.utils.MdcKeys;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;

/**
 * Grades a finished interview and stores the verdict.
 *
 * <p>Until this existed the grader was only ever called by the eval runners, so a real agent
 * interview produced a transcript and no assessment at all — the candidate answered eleven
 * questions and nobody was scored. The loop, the tools, the transcript isolation and the grader all
 * worked; nothing joined the last two together outside a benchmark.
 *
 * <p><b>Off the agent pool.</b> Grading is one model call and takes 40 to 70 seconds against the
 * local 7B. Doing it inline in the orchestrator's {@code finally} would hold an agent thread for
 * that long after the candidate had already gone, and the agent pool is the constraint the
 * 48-concurrent figure is bounded by. {@code gradingExecutor} was declared for this in Phase 6 and
 * had no caller until now.
 *
 * <p><b>Never throws into the caller.</b> A grading failure must not take down session cleanup: the
 * transcript is the artefact that cannot be regenerated, and a verdict can always be recomputed
 * from it later.
 */
@Slf4j
@Component
public class VerdictWriter {

    private final GraderAgent grader;
    private final GradingInputFactory inputFactory;
    private final JobService jobService;
    private final InterviewVerdictMapper verdictMapper;
    private final ObjectMapper objectMapper;
    private final Executor gradingExecutor;

    public VerdictWriter(GraderAgent grader,
                         GradingInputFactory inputFactory,
                         JobService jobService,
                         InterviewVerdictMapper verdictMapper,
                         ObjectMapper objectMapper,
                         @Qualifier("gradingExecutor") Executor gradingExecutor) {
        this.grader = grader;
        this.inputFactory = inputFactory;
        this.jobService = jobService;
        this.verdictMapper = verdictMapper;
        this.objectMapper = objectMapper;
        this.gradingExecutor = gradingExecutor;
    }

    /**
     * Queue this session for grading.
     *
     * <p>The {@link GradingInput} is built <em>now</em>, on the caller's thread, while the session
     * object is still live. Passing the session itself and building it later would hand a mutable
     * object to another thread after the orchestrator has removed it from the store.
     */
    public void gradeLater(InterviewSession session) {
        if (session.getTurns().isEmpty()) {
            // Nothing was asked or answered. Grading an empty transcript would produce a score for
            // a candidate who never spoke, which is worse than no score.
            log.info("session {} has no turns, not grading", session.getSessionId());
            return;
        }
        Job job = jobService.getDetail(session.getJobId());
        GradingInput input = inputFactory.from(session, job, true);
        String sessionId = session.getSessionId();
        String candidateId = session.getCandidateId();
        String jobId = session.getJobId();

        MdcKeys.putSession(sessionId, candidateId);
        gradingExecutor.execute(MdcKeys.wrap(
                () -> grade(sessionId, candidateId, jobId, input)));
    }

    private void grade(String sessionId, String candidateId, String jobId, GradingInput input) {
        long started = System.currentTimeMillis();
        try {
            Verdict verdict = grader.grade(input);
            InterviewVerdictPO po = new InterviewVerdictPO();
            po.setSessionId(sessionId);
            po.setCandidateId(candidateId);
            po.setJobId(jobId);
            po.setOverall(verdict.overall());
            po.setRecommendation(verdict.recommendation());
            po.setSummary(verdict.summary());
            po.setDimensionsJson(objectMapper.writeValueAsString(verdict.dimensions()));
            po.setGradedMs(System.currentTimeMillis() - started);
            po.setCreatedTime(LocalDateTime.now());
            verdictMapper.insert(po);
            log.info("session {} graded: overall={} {} in {} ms",
                    sessionId, verdict.overall(), verdict.recommendation(), po.getGradedMs());
        } catch (Exception e) {
            // Deliberately broad. The transcript is already persisted and is the artefact that
            // cannot be regenerated; a verdict can be recomputed from it whenever the model is back.
            log.error("could not grade session {}", sessionId, e);
        }
    }
}
