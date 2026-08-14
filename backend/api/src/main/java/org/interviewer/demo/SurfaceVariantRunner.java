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
 * C4 — does the grader score the same knowledge differently depending on how it is expressed?
 *
 * <p>Each variant of an answer carries identical technical content and differs only on the surface:
 * non-native phrasing, hedging, padding, terseness. Anything but a flat result means the grader is
 * reading fluency as competence — which in a hiring tool is not a rounding error, it is the failure
 * mode that matters most, and it lands hardest on exactly the candidates least able to argue with
 * it.
 *
 * <p>Measuring one kind of bias and calling it "we measured bias" would be worse than not
 * measuring: this sits beside the length-bias metric, not in place of it.
 *
 * <pre>
 *   mvn -pl api spring-boot:run -Dspring-boot.run.profiles=dev,surface-variants
 * </pre>
 */
@Slf4j
@Profile("surface-variants")
@Component
public class SurfaceVariantRunner implements ApplicationRunner {

    private static final Path FIXTURE = Path.of("../../eval/surface_variants.json");
    private static final Path OUTPUT = Path.of("../../eval/surface_results.json");

    private final GraderAgent grader;
    private final JobService jobService;
    private final OllamaClient llm;
    private final ObjectMapper objectMapper;

    public SurfaceVariantRunner(GraderAgent grader,
                                JobService jobService,
                                OllamaClient llm,
                                ObjectMapper objectMapper) {
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
        List<String> variants = new ArrayList<>();
        fixture.get("variants").forEach(v -> variants.add(v.asText()));

        Job job = jobService.getDetail(anyJobId());
        String rubric = job == null ? "Assess technical competence."
                : (job.getGraderPrompt() != null && !job.getGraderPrompt().isBlank()
                        ? job.getGraderPrompt() : job.getPrompt());

        Map<String, Object> results = new LinkedHashMap<>();
        for (String variant : variants) {
            GradingInput input = buildTranscript(fixture, variant, rubric,
                    job == null ? "SDE" : job.getJobName());

            long started = System.currentTimeMillis();
            Verdict verdict = grader.grade(input);
            long elapsed = System.currentTimeMillis() - started;

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("overall", verdict.overall());
            record.put("recommendation", verdict.recommendation());
            Map<String, Integer> dims = new LinkedHashMap<>();
            for (DimensionScore d : verdict.dimensions()) {
                dims.put(d.name(), d.score());
            }
            record.put("dimensions", dims);
            record.put("summary", verdict.summary());
            record.put("transcriptChars", transcriptLength(input));
            record.put("gradingMs", elapsed);
            results.put(variant, record);

            log.info("  {} overall={} {} {}", String.format("%-11s", variant),
                    verdict.overall(), verdict.recommendation(), dims);
        }

        Files.writeString(OUTPUT, objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(Map.of(
                        "note", "C4 surface-feature sensitivity. Identical technical content "
                                + "across variants; only phrasing differs.",
                        "results", results)));
        log.info("surface variant results -> {}", OUTPUT.toAbsolutePath().normalize());
    }

    /** Builds one complete interview transcript in a single surface style. */
    private GradingInput buildTranscript(JsonNode fixture, String variant, String rubric,
                                         String jobName) {
        List<TranscriptTurn> turns = new ArrayList<>();
        for (Iterator<JsonNode> it = fixture.get("questions").elements(); it.hasNext(); ) {
            JsonNode question = it.next();
            turns.add(new TranscriptTurn(turns.size(), TranscriptTurn.QUESTION,
                    question.get("question").asText(), 0));
            turns.add(new TranscriptTurn(turns.size(), TranscriptTurn.ANSWER,
                    question.get("answers").get(variant).asText(), 60));
        }
        return new GradingInput("surface-" + variant, jobName, rubric, turns,
                turns.size() * 30L, List.of());
    }

    private int transcriptLength(GradingInput input) {
        return input.turns().stream()
                .filter(t -> TranscriptTurn.ANSWER.equals(t.kind()))
                .mapToInt(t -> t.text().length())
                .sum();
    }

    /** Any job will do - the rubric is what matters here, not which role it belongs to. */
    private String anyJobId() {
        var jobs = jobService.queryList(1, 1);
        if (jobs.getRows().isEmpty()) {
            return null;
        }
        // queryList returns JobVO, not Job. Getting this wrong is silent: the caller falls back
        // to a default rubric and every grade is produced against the wrong instructions.
        Object row = jobs.getRows().get(0);
        return row instanceof JobVO vo ? vo.getJobId() : null;
    }
}
