package org.interviewer.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.interviewer.entity.Job;
import org.interviewer.entity.vo.JobVO;
import org.interviewer.entity.grading.DimensionScore;
import org.interviewer.entity.grading.GradingInput;
import org.interviewer.entity.grading.TranscriptTurn;
import org.interviewer.entity.grading.Verdict;
import org.interviewer.grader.GraderAgent;
import org.interviewer.llm.OllamaClient;
import org.interviewer.service.JobService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Grades a simulated cohort: 3 quality tiers × 4 surface profiles = 12 participants.
 *
 * <p>The design is factorial on purpose. Quality genuinely differs between tiers; surface differs
 * within a tier while the facts stay identical. Grading all twelve separates two questions that C4
 * could only gesture at with one participant per cell:
 *
 * <ul>
 *   <li>Does the grader <em>rank by competence</em>? A grader that cannot order strong above weak
 *       is not measuring the candidate at all, and no amount of agreement with a human would save
 *       it.</li>
 *   <li>Does phrasing move the score <em>independently of competence</em>? The same surface shift
 *       applied at three different competence levels, so a consistent delta is not an artefact of
 *       one answer.</li>
 * </ul>
 *
 * <p><b>What this deliberately does not produce is a human ceiling.</b> There are no human labels
 * here. Generating them would turn "grader vs human agreement" into "grader vs the script that
 * wrote both sides", which is a number that looks like evidence and is not. That row stays empty
 * until real people rate real transcripts.
 *
 * <pre>
 *   mvn -pl api spring-boot:run -Dspring-boot.run.profiles=dev,cohort
 * </pre>
 */
@Slf4j
@Profile("cohort")
@Component
public class CohortRunner implements ApplicationRunner {

    private static final Path FIXTURE = Path.of("../../eval/simulated_cohort.json");
    private static final Path OUTPUT = Path.of("../../eval/cohort_results.json");

    /** Designed rank, used as the ground truth the grader is scored against. */
    private static final Map<String, Integer> DESIGNED_RANK =
            Map.of("weak", 1, "mixed", 2, "strong", 3);

    private final GraderAgent grader;
    private final JobService jobService;
    private final OllamaClient llm;
    private final ObjectMapper objectMapper;

    public CohortRunner(GraderAgent grader, JobService jobService,
                        OllamaClient llm, ObjectMapper objectMapper) {
        this.grader = grader;
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
        JsonNode fixture = objectMapper.readTree(Files.readString(FIXTURE));
        List<String> tiers = List.of("strong", "mixed", "weak");
        List<String> surfaces = new ArrayList<>();
        fixture.get("surface_profiles").forEach(p -> surfaces.add(p.asText()));

        Job job = anyJob();
        String rubric = job == null ? "Assess technical competence."
                : (job.getGraderPrompt() != null && !job.getGraderPrompt().isBlank()
                        ? job.getGraderPrompt() : job.getPrompt());
        String jobName = job == null ? "SDE" : job.getJobName();

        List<Map<String, Object>> participants = new ArrayList<>();
        int n = 0;
        for (String tier : tiers) {
            for (String surface : surfaces) {
                String id = tier + "-" + surface;
                GradingInput input = transcript(fixture, tier, surface, rubric, jobName, id);

                long started = System.currentTimeMillis();
                Verdict verdict = grader.grade(input);
                long elapsed = System.currentTimeMillis() - started;

                Map<String, Integer> dims = new LinkedHashMap<>();
                for (DimensionScore d : verdict.dimensions()) {
                    dims.put(d.name(), d.score());
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("designedTier", tier);
                row.put("designedRank", DESIGNED_RANK.get(tier));
                row.put("surface", surface);
                row.put("overall", verdict.overall());
                row.put("recommendation", verdict.recommendation());
                row.put("dimensions", dims);
                row.put("transcriptChars", chars(input));
                row.put("gradingMs", elapsed);
                participants.add(row);

                n++;
                log.info("  [{}/12] {} -> overall={} {} {}", n, String.format("%-18s", id),
                        verdict.overall(), verdict.recommendation(), dims);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("note", "Simulated cohort. Quality tiers are designed in; surface profiles hold "
                + "facts constant within a tier. NOT a human-labeled set and NOT a human ceiling.");
        out.put("participants", participants);
        Files.writeString(OUTPUT, objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(out));
        log.info("cohort results -> {}", OUTPUT.toAbsolutePath().normalize());
    }

    private GradingInput transcript(JsonNode fixture, String tier, String surface,
                                    String rubric, String jobName, String id) {
        List<TranscriptTurn> turns = new ArrayList<>();
        for (Iterator<JsonNode> it = fixture.get("questions").elements(); it.hasNext(); ) {
            JsonNode question = it.next();
            turns.add(new TranscriptTurn(turns.size(), TranscriptTurn.QUESTION,
                    question.get("question").asText(), 0));
            turns.add(new TranscriptTurn(turns.size(), TranscriptTurn.ANSWER,
                    question.get("tiers").get(tier).get(surface).asText(), 60));
        }
        return new GradingInput("cohort-" + id, jobName, rubric, turns, turns.size() * 30L,
                List.of());
    }

    private int chars(GradingInput input) {
        return input.turns().stream()
                .filter(t -> TranscriptTurn.ANSWER.equals(t.kind()))
                .mapToInt(t -> t.text().length()).sum();
    }

    private Job anyJob() {
        var jobs = jobService.queryList(1, 1);
        if (jobs.getRows().isEmpty()) {
            return null;
        }
        // queryList returns JobVO, not Job. Getting this wrong is silent: the caller falls back
        // to a default rubric and every grade is produced against the wrong instructions.
        Object row = jobs.getRows().get(0);
        return row instanceof JobVO vo ? jobService.getDetail(vo.getJobId()) : null;
    }
}
