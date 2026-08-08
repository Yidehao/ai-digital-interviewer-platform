package org.interviewer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.interviewer.entity.InterviewRecord;
import org.interviewer.entity.Job;
import org.interviewer.entity.bo.AnswerBO;
import org.interviewer.entity.bo.SubmitAnswerBO;
import org.interviewer.entity.ollama.OllamaGenerateChunk;
import org.interviewer.service.InterviewRecordService;
import org.interviewer.service.JobService;
import org.interviewer.utils.OllamaConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OllamaTask provides asynchronous call to local Ollama for interview analysis.
 * Uses the same input (SubmitAnswerBO) and output (InterviewRecord) as before (previously ChatGLM).
 */
@Component
@Slf4j
public class OllamaTask {

    private static final String APPLICATION_JSON = "application/json; charset=utf-8";
    private static final int CONNECT_TIMEOUT = 60;
    private static final int READ_TIMEOUT = 600;
    private static final int WRITE_TIMEOUT = 120;

    @Resource
    private JobService jobService;

    @Resource
    private InterviewRecordService interviewRecordService;

    @Resource
    private OllamaConfig ollamaConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async
    public void display(SubmitAnswerBO submitAnswerBO) {
        log.info("Starting asynchronous Ollama analysis...");

        String jobId = submitAnswerBO.getJobId();
        Job job = jobService.getDetail(jobId);
        String prompt = job != null ? job.getPrompt() : "";

        List<AnswerBO> answerList = submitAnswerBO.getQuestionAnswerList();
        StringBuilder content = new StringBuilder();
        content.append("[Instruction: For each item below, \"Reference Answer\" is the ideal/standard answer for comparison only. \"Candidate's Answer\" is what the candidate actually said (from speech-to-text). Please evaluate and comment on the candidate's answer, using the reference only as a standard—do not treat the reference as what the candidate said.]\n\n");

        for (AnswerBO answer : answerList) {
            content.append("Question: ").append(answer.getQuestion()).append("\n");
            content.append("Reference Answer (standard for comparison only): ").append(answer.getReferenceAnswer()).append("\n");
            content.append("Candidate's Answer (actual, from speech-to-text): ").append(answer.getAnswerContent()).append("\n\n");
        }

        String submitContent = prompt + "\n\n" + content;
        log.debug("Ollama prompt length: {} chars", submitContent.length());

        String baseUrl = ollamaConfig.getBaseUrl().replaceAll("/$", "");
        String url = baseUrl + "/api/generate";

        Map<String, Object> body = new HashMap<>();
        body.put("model", ollamaConfig.getModel());
        body.put("prompt", submitContent);
        body.put("stream", true);

        try {
            String bodyJson = objectMapper.writeValueAsString(body);
            Request request = new Request.Builder()
                    .url(url)
                    .header("Content-Type", APPLICATION_JSON)
                    .post(RequestBody.create(MediaType.parse(APPLICATION_JSON), bodyJson))
                    .build();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                    .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                    .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                ResponseBody responseBody = response.body();
                if (!response.isSuccessful() || responseBody == null) {
                    String bodyStr = responseBody != null ? responseBody.string() : "";
                    log.error("Ollama request failed: {} {} | url={} model={} | body: {}",
                            response.code(), response.message(), url, ollamaConfig.getModel(), bodyStr);
                    log.error("If 404: ensure Ollama is running (ollama serve) and model exists (ollama list / ollama pull <model>)");
                    return;
                }

                StringBuilder fullResult = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) continue;
                        OllamaGenerateChunk chunk = objectMapper.readValue(line, OllamaGenerateChunk.class);
                        if (chunk.getResponse() != null) {
                            fullResult.append(chunk.getResponse());
                        }
                        if (Boolean.TRUE.equals(chunk.getDone())) {
                            break;
                        }
                    }
                }

                String finalResult = fullResult.toString();
                InterviewRecord record = new InterviewRecord();
                record.setCandidateId(submitAnswerBO.getCandidateId());
                record.setTakeTime(submitAnswerBO.getTotalSeconds());
                record.setJobName(job != null ? job.getJobName() : "");
                record.setAnswerContent(content.toString());
                record.setResult(finalResult);
                record.setCreateTime(LocalDateTime.now());
                record.setUpdatedTime(LocalDateTime.now());

                interviewRecordService.save(record);
                log.info("Ollama analysis saved for candidate {}", submitAnswerBO.getCandidateId());
            }
        } catch (Exception e) {
            log.error("Ollama analysis failed", e);
        }
    }
}
