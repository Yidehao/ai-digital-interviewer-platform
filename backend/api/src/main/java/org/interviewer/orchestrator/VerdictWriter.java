package org.interviewer.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.interviewer.entity.InterviewVerdictPO;
import org.interviewer.entity.Job;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.TurnKind;
import org.interviewer.entity.grading.GradingInput;
import org.interviewer.entity.grading.GradingOutcome;
import org.interviewer.entity.grading.Verdict;
import org.interviewer.grader.GraderAgent;
import org.interviewer.grader.GradingInputFactory;
import org.interviewer.mapper.InterviewVerdictMapper;
import org.interviewer.service.JobService;
import org.interviewer.utils.LlmProperties;
import org.interviewer.utils.MdcKeys;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

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
    private final LlmProperties properties;

    public VerdictWriter(GraderAgent grader,
                         GradingInputFactory inputFactory,
                         JobService jobService,
                         InterviewVerdictMapper verdictMapper,
                         ObjectMapper objectMapper,
                         LlmProperties properties,
                         @Qualifier("gradingExecutor") Executor gradingExecutor) {
        this.properties = properties;
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
        // The check is for ANSWERS, not for turns. `turns.isEmpty()` was the obvious guard and the
        // wrong one: a session that asked a question and received nothing has one turn, passes an
        // is-empty check, and gets graded - producing a score for a candidate who never spoke.
        //
        // Found by a real run. A credentials failure broke speech-to-text mid-interview, every
        // answer was refused, the answer-gate timed out after five minutes, and the session closed
        // with CANDIDATE_TIMEOUT holding exactly one QUESTION turn. That is a candidate whose
        // microphone failed, and the only honest thing to say about them is nothing.
        boolean answered = session.getTurns().stream()
                .anyMatch(t -> t.getKind() == TurnKind.ANSWER);
        if (!answered) {
            log.info("session {} has no answers ({} turns, terminal reason {}), not grading",
                    session.getSessionId(), session.getTurns().size(), session.getTerminalReason());
            return;
        }
        Job job = jobService.getDetail(session.getJobId());
        GradingInput input = inputFactory.from(session, job, true);
        String sessionId = session.getSessionId();
        String candidateId = session.getCandidateId();
        String jobId = session.getJobId();

        MdcKeys.putSession(sessionId, candidateId);
        try {
            gradingExecutor.execute(MdcKeys.wrap(
                    () -> grade(sessionId, candidateId, jobId, input)));
        } catch (RejectedExecutionException e) {
            // The grading queue is full (2 threads, 100 slots). This THREW INTO SESSION CLEANUP and
            // took the rest of it with it: releaseCandidate, store.delete and emitters.complete all
            // sit after this call, so under load every finished session leaked its candidate claim
            // for the claim's three-hour TTL. A load test found 134 orphaned claims with the pools
            // completely idle.
            //
            // It also corrupted the load test's own numbers - sessions that skipped cleanup
            // "finished" faster, so throughput appeared to jump from 15 to 56 sessions/s at the
            // exact concurrency where the grading queue started overflowing. A bug that makes the
            // benchmark look better is the worst kind.
            //
            // Dropping the grade is the right trade: the transcript is persisted and a verdict can
            // be recomputed from it whenever, but a locked-out candidate cannot re-interview.
            log.warn("grading queue full, session {} left ungraded - recompute from the transcript",
                    sessionId);
        }
    }

    private void grade(String sessionId, String candidateId, String jobId, GradingInput input) {
        long started = System.currentTimeMillis();
        try {
            GradingOutcome outcome = grader.grade(input);
            Verdict verdict = outcome.verdict();
            InterviewVerdictPO po = new InterviewVerdictPO();
            po.setSessionId(sessionId);
            po.setCandidateId(candidateId);
            po.setJobId(jobId);
            po.setOverall(verdict.overall());
            po.setRecommendation(verdict.recommendation());
            po.setSummary(verdict.summary());
            po.setDimensionsJson(objectMapper.writeValueAsString(verdict.dimensions()));
            po.setClaimsJson(objectMapper.writeValueAsString(verdict.claims()));
            po.setSamples(outcome.samples());
            po.setDimensionSpread(outcome.maxDimensionSpread());
            po.setOverallSpread(outcome.overallSpread());
            po.setNeedsHumanReview(outcome.needsHumanReview());
            po.setReviewReason(outcome.reviewReason());
            po.setAdvisory(outcome.advisory());
            // Provenance, so the verdict can be rebuilt and re-run later. A candidate asking why
            // they scored a 2 deserves better than "the model said so, and we no longer know which
            // model, which rubric, or which prompt".
            po.setModel(properties.getModel());
            po.setRubricHash(sha256(input.rubric()));
            po.setPromptHash(sha256(grader.renderTranscript(input)));
            po.setSchemaVersion(grader.schemaVersion());
            po.setGradedMs(System.currentTimeMillis() - started);
            po.setCreatedTime(LocalDateTime.now());
            verdictMapper.insert(po);
            log.info("session {} graded: overall={} {} in {} ms ({} samples, spread {}{})",
                    sessionId, verdict.overall(), verdict.recommendation(), po.getGradedMs(),
                    outcome.samples(), outcome.maxDimensionSpread(),
                    outcome.needsHumanReview() ? ", NEEDS HUMAN REVIEW" : "");
        } catch (Exception e) {
            // Deliberately broad. The transcript is already persisted and is the artefact that
            // cannot be regenerated; a verdict can be recomputed from it whenever the model is back.
            log.error("could not grade session {}", sessionId, e);
        }
    }

    /** Content hash, so a stored verdict names the exact text that produced it. */
    private String sha256(String text) {
        if (text == null) {
            return null;
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : digest) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16))
                   .append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (Exception e) {
            // Provenance failing must not cost the verdict itself.
            log.warn("could not hash for provenance: {}", e.getMessage());
            return null;
        }
    }
}
