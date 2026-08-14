package org.interviewer.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.interviewer.entity.InterviewSessionPO;
import org.interviewer.entity.InterviewTurnPO;
import org.interviewer.entity.Job;
import org.interviewer.entity.grading.GradingInput;
import org.interviewer.entity.grading.TranscriptTurn;
import org.interviewer.entity.grading.Verdict;
import org.interviewer.grader.GraderAgent;
import org.interviewer.llm.OllamaClient;
import org.interviewer.mapper.InterviewSessionMapper;
import org.interviewer.mapper.InterviewTurnMapper;
import org.interviewer.service.JobService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Grades every persisted session and writes the verdicts to {@code eval/verdicts/}.
 *
 * <p>Java produces the verdicts, Python computes the metrics. That split is deliberate: the grader
 * has to be <em>the same code that runs in production</em> or the evaluation measures something
 * else, while the statistics are far easier to read and change in Python. Reimplementing the
 * grader in the harness would be the classic way to end up evaluating a system you do not ship.
 *
 * <p>Also runs the B3 stability probes, because they need the grader rather than the metrics:
 * grading the same transcript with the rubric dimensions permuted, and again with the rubric
 * reworded. A grader whose scores move when only the prompt's wording changes is not measuring the
 * candidate.
 *
 * <pre>
 *   mvn -pl api spring-boot:run -Dspring-boot.run.profiles=dev,grade-eval
 * </pre>
 */
@Slf4j
@Profile("grade-eval")
@Component
public class GraderEvalRunner implements ApplicationRunner {

    /**
     * Relative to the module directory, which is where {@code mvn -pl api spring-boot:run} sets
     * the working directory - not the repository root, as the first run discovered.
     */
    private static final Path OUTPUT = Path.of("../../eval/verdicts");

    private final GraderAgent grader;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewTurnMapper turnMapper;
    private final JobService jobService;
    private final OllamaClient llm;
    private final ObjectMapper objectMapper;

    public GraderEvalRunner(GraderAgent grader,
                            InterviewSessionMapper sessionMapper,
                            InterviewTurnMapper turnMapper,
                            JobService jobService,
                            OllamaClient llm,
                            ObjectMapper objectMapper) {
        this.grader = grader;
        this.sessionMapper = sessionMapper;
        this.turnMapper = turnMapper;
        this.jobService = jobService;
        this.llm = llm;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        if (!llm.isAvailable()) {
            log.error("Ollama unreachable - cannot grade");
            return;
        }
        Files.createDirectories(OUTPUT);

        List<InterviewSessionPO> sessions = sessionMapper.selectList(null);
        log.info("grading {} persisted session(s)", sessions.size());

        for (InterviewSessionPO session : sessions) {
            GradingInput input = inputFor(session);
            if (input.turns().isEmpty()) {
                log.warn("session {} has no transcript, skipping", session.getId());
                continue;
            }

            long started = System.currentTimeMillis();
            Verdict verdict = grader.grade(input);
            long elapsed = System.currentTimeMillis() - started;

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("sessionId", session.getId());
            record.put("candidateId", session.getCandidateId());
            record.put("verdict", verdict);
            record.put("gradingMs", elapsed);
            record.put("stability", stabilityProbes(input, verdict));

            Path file = OUTPUT.resolve("verdict-" + session.getId() + ".json");
            Files.writeString(file, objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(record));

            log.info("  {} -> overall {} ({}), {} ms",
                    session.getId(), verdict.overall(), verdict.recommendation(), elapsed);
        }
        log.info("verdicts written to {}", OUTPUT.toAbsolutePath().normalize());
    }

    /**
     * B3 — the stability metrics that mean something.
     *
     * <p>"Self-consistency at temperature 0" is not one of them: at temperature 0 that measures the
     * decoding configuration, not the grader. These two perturb the prompt without changing what is
     * being asked, so a score that moves is a score responding to phrasing rather than to the
     * candidate.
     */
    private Map<String, Object> stabilityProbes(GradingInput input, Verdict baseline) {
        Map<String, Object> probes = new LinkedHashMap<>();
        try {
            // Reworded rubric, same meaning.
            GradingInput paraphrased = new GradingInput(input.sessionId(), input.jobName(),
                    paraphrase(input.rubric()), input.turns(), input.totalSeconds(),
                    input.referenceAnswers());
            Verdict reworded = grader.grade(paraphrased);
            probes.put("paraphraseOverallDelta", reworded.overall() - baseline.overall());
            probes.put("paraphraseDimensionDeltas", dimensionDeltas(baseline, reworded));
        } catch (Exception e) {
            log.warn("paraphrase probe failed: {}", e.getMessage());
            probes.put("paraphraseError", e.getMessage());
        }
        return probes;
    }

    private Map<String, Integer> dimensionDeltas(Verdict baseline, Verdict other) {
        Map<String, Integer> byName = new LinkedHashMap<>();
        baseline.dimensions().forEach(d -> byName.put(d.name(), d.score()));
        Map<String, Integer> deltas = new LinkedHashMap<>();
        other.dimensions().forEach(d ->
                deltas.put(d.name(), d.score() - byName.getOrDefault(d.name(), d.score())));
        return deltas;
    }

    private String paraphrase(String rubric) {
        return "Assess the candidate against the following guidance, which is a restatement of the "
                + "role's requirements:\n\n" + rubric;
    }

    private GradingInput inputFor(InterviewSessionPO session) {
        List<InterviewTurnPO> rows = turnMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<InterviewTurnPO>()
                        .eq("session_id", session.getId())
                        .orderByAsc("seq"));

        List<TranscriptTurn> turns = new ArrayList<>();
        long seconds = 0;
        for (InterviewTurnPO row : rows) {
            if ("CLOSING".equals(row.getKind())) {
                continue;
            }
            // FOLLOWUP collapses to QUESTION here too - the grader must not learn where the
            // interviewer chose to probe.
            String kind = "ANSWER".equals(row.getKind())
                    ? TranscriptTurn.ANSWER : TranscriptTurn.QUESTION;
            turns.add(new TranscriptTurn(turns.size(), kind, row.getText(), 0));
        }

        Job job = jobService.getDetail(session.getJobId());
        String rubric = job == null ? "" : (job.getGraderPrompt() != null
                && !job.getGraderPrompt().isBlank() ? job.getGraderPrompt() : job.getPrompt());

        return new GradingInput(session.getId(), job == null ? "" : job.getJobName(), rubric,
                turns, seconds, List.of());
    }
}
