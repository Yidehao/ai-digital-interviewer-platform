package org.interviewer.controller;

import lombok.extern.slf4j.Slf4j;
import org.interviewer.agent.stream.SessionEmitter;
import org.interviewer.grace.result.GraceJSONResult;
import org.interviewer.grace.result.ResponseStatusEnum;
import org.interviewer.orchestrator.InterviewOrchestrator;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Transport for the agent interview. Composition only — no logic lives here.
 *
 * <pre>
 *   GET  /interview/{candidateId}/stream    open the event stream, start the interview
 *   POST /interview/{sessionId}/answer      submit a transcribed answer
 * </pre>
 *
 * <p>The existing scripted endpoints are untouched and still serve every job, because
 * {@code job.interview_mode} defaults to {@code scripted}. Nothing routes here until a job is
 * deliberately opted in, which is what makes this phase safe to ship.
 *
 * <p>Demo:
 * <pre>
 *   curl -N http://127.0.0.1:8080/interview/{candidateId}/stream
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("interview")
public class InterviewStreamController {

    private final InterviewOrchestrator orchestrator;

    public InterviewStreamController(InterviewOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Opens the stream and starts the interview.
     *
     * <p>Returns {@code SseEmitter} rather than the project's {@code GraceJSONResult} envelope
     * because this response is a stream, not a document. That is the one place in the API where
     * the envelope convention does not apply, and it is worth noticing rather than "fixing".
     */
    @GetMapping(value = "{candidateId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String candidateId) {
        return orchestrator.start(candidateId)
                .map(SessionEmitter::raw)
                .orElseGet(() -> {
                    // Unknown candidate, or one already mid-interview. Complete immediately with a
                    // named event: a client that receives nothing cannot tell the difference
                    // between "refused" and "server is slow".
                    SseEmitter emitter = new SseEmitter(0L);
                    try {
                        emitter.send(SseEmitter.event().name("error")
                                .data("cannot start an interview for this candidate"));
                    } catch (Exception ignored) {
                        // Client already gone; nothing useful to do.
                    }
                    emitter.complete();
                    return emitter;
                });
    }

    @PostMapping("{sessionId}/answer")
    public GraceJSONResult answer(@PathVariable String sessionId,
                                  @RequestParam String turnId,
                                  @RequestParam String transcript,
                                  @RequestParam(required = false) Double sttConfidence) {
        boolean accepted = orchestrator.submitAnswer(sessionId, turnId, transcript, sttConfidence);
        return accepted
                ? GraceJSONResult.ok()
                : GraceJSONResult.errorCustom(ResponseStatusEnum.SYSTEM_OPERATION_ERROR);
    }
}
