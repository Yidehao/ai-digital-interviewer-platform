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

    /**
     * Which page the client should open for this candidate.
     *
     * <p>Asked before the interview starts, so the scripted client stays the default and untouched
     * for every job that has not been switched over.
     */
    @GetMapping("{candidateId}/mode")
    public GraceJSONResult mode(@PathVariable String candidateId) {
        return GraceJSONResult.ok(java.util.Map.of(
                "mode", orchestrator.interviewMode(candidateId)));
    }

    /**
     * Start an interview without holding a stream open.
     *
     * <p>For the two uni-app targets that have no {@code EventSource}: app-plus and mp-weixin. The
     * interview itself is identical — same orchestrator, same loop, same session lifecycle — only
     * the way questions reach the client differs.
     */
    @PostMapping("{candidateId}/start")
    public GraceJSONResult start(@PathVariable String candidateId) {
        return orchestrator.startPolling(candidateId)
                .map(sessionId -> GraceJSONResult.ok(java.util.Map.of("sessionId", sessionId)))
                .orElseGet(() -> GraceJSONResult.errorCustom(
                        ResponseStatusEnum.SYSTEM_OPERATION_ERROR));
    }

    /**
     * The question the candidate should answer now, or nothing yet.
     *
     * <p><b>Short poll, deliberately not long poll.</b> Long polling would give lower latency and
     * fewer requests, and it would pin one request thread per waiting candidate for as long as the
     * model takes to think — measured at tens of seconds. Phase 8 established that this node
     * sustains 48 concurrent sessions; 48 parked Tomcat threads would spend that result to save
     * some polling traffic, and the traffic is cheap. A client polling every 1.5 s issues a few
     * dozen requests per question, each of which reads a map and returns.
     *
     * <p>{@code afterSeq} is the last question the client already holds, so a slow answer does not
     * make it re-display the same question on every poll.
     */
    @GetMapping("{sessionId}/poll")
    public GraceJSONResult poll(@PathVariable String sessionId,
                                @RequestParam(defaultValue = "-1") int afterSeq) {
        return GraceJSONResult.ok(orchestrator.poll(sessionId, afterSeq));
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
