package org.interviewer.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.interviewer.agent.InterviewerAgent;
import org.interviewer.agent.SystemPromptBuilder;
import org.interviewer.agent.session.SessionPersister;
import org.interviewer.agent.session.SessionStore;
import org.interviewer.agent.stream.AgentMetrics;
import org.interviewer.agent.stream.EmitterRegistry;
import org.interviewer.agent.stream.SessionEmitter;
import org.interviewer.agent.stream.SseAgentEvents;
import org.interviewer.entity.Candidate;
import org.interviewer.entity.Job;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.PolledQuestion;
import org.interviewer.entity.agent.Turn;
import org.interviewer.entity.agent.SessionState;
import org.interviewer.entity.agent.TurnKind;
import org.interviewer.entity.ollama.ChatMessage;
import org.interviewer.service.CandidateService;
import org.interviewer.service.JobService;
import org.interviewer.utils.MdcKeys;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns an interview's lifecycle: create it, run it on a background thread, feed it answers, and
 * make sure it is persisted whatever happens.
 *
 * <p>This is the only place that knows both HTTP and the agent. The controller does transport, the
 * agent does the loop, and neither knows about the other.
 *
 * <p>Concurrency is the interesting part. Two threads can touch one session — the loop thread and a
 * {@code POST /answer} thread — so answers are appended under the session's monitor and the loop
 * observes them on its next turn. For a single node that is enough; the Redis turn lock exists for
 * the multi-node case, where {@code EmitterRegistry} being node-local already requires sticky
 * sessions.
 */
@Slf4j
@Service
public class InterviewOrchestrator {

    private static final long STREAM_TIMEOUT_MS = 45L * 60 * 1000;

    private final InterviewerAgent agent;
    private final SessionStore store;
    private final SessionPersister persister;
    private final EmitterRegistry emitters;
    private final AgentMetrics metrics;
    private final SystemPromptBuilder promptBuilder;
    private final CandidateService candidateService;
    private final JobService jobService;
    private final Clock clock;

    /**
     * Injected and submitted to explicitly, rather than using {@code @Async} on the method below.
     *
     * <p>{@code @Async} would not have worked here and the failure is silent: {@code start()} calls
     * the loop on {@code this}, which bypasses Spring's proxy entirely, so the annotation is
     * ignored and the interview runs on the request thread. The symptom is not an error - it is an
     * SSE endpoint that returns HTTP 200 and then never sends anything, because the controller
     * cannot return until the whole interview has finished.
     *
     * <p>Submitting to the executor directly removes the trap rather than working around it.
     */
    private final java.util.concurrent.Executor agentExecutor;

    public InterviewOrchestrator(InterviewerAgent agent,
                                 SessionStore store,
                                 SessionPersister persister,
                                 EmitterRegistry emitters,
                                 AgentMetrics metrics,
                                 SystemPromptBuilder promptBuilder,
                                 CandidateService candidateService,
                                 JobService jobService,
                                 Clock clock,
                                 @Qualifier("agentExecutor")
                                 java.util.concurrent.Executor agentExecutor) {
        this.agent = agent;
        this.store = store;
        this.persister = persister;
        this.emitters = emitters;
        this.metrics = metrics;
        this.promptBuilder = promptBuilder;
        this.candidateService = candidateService;
        this.jobService = jobService;
        this.clock = clock;
        this.agentExecutor = agentExecutor;
    }

    /**
     * Create a session and open its stream.
     *
     * @return the emitter the controller returns to the client, or empty when the candidate is
     *         unknown or already has an interview in flight
     */
    public Optional<SessionEmitter> start(String candidateId) {
        Candidate candidate = candidateService.getDetail(candidateId);
        if (candidate == null) {
            return Optional.empty();
        }
        Job job = jobService.getDetail(candidate.getJobId());
        if (job == null) {
            return Optional.empty();
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "");
        // One interview per candidate. A double-tap on "start" must not produce two.
        if (!store.claimCandidate(candidateId, sessionId)) {
            log.info("candidate {} already has an interview in flight", candidateId);
            return Optional.empty();
        }

        InterviewSession session = new InterviewSession();
        session.setSessionId(sessionId);
        session.setCandidateId(candidateId);
        session.setJobId(job.getId());
        session.setInterviewerId(job.getInterviewerId());
        session.setStartedAt(clock.instant());
        // Built once and reused as the identical byte string on every turn. Rebuilding it - even
        // reformatting whitespace - busts Ollama's prefix cache and silently costs a full prompt
        // re-evaluation, measured at ~20 s on a 1.5k-token prompt.
        session.setSystemPrompt(promptBuilder.build(job.getPrompt(), 5));
        store.save(session);

        SessionEmitter emitter = emitters.register(sessionId, STREAM_TIMEOUT_MS);
        emitter.send("session", java.util.Map.of("sessionId", sessionId));
        // MdcKeys.wrap, not a bare lambda. MDC is thread-local, so the session id would be lost
        // the moment the work crosses onto the agent pool - precisely when the log lines start
        // being worth reading. wrap() carries the context over and clears it afterwards, because a
        // pool thread that kept it would stamp the next interview with this one's identity.
        MdcKeys.putSession(sessionId, candidateId);
        agentExecutor.execute(MdcKeys.wrap(() -> runInterview(sessionId, emitter)));
        return Optional.of(emitter);
    }

    /** Runs the loop off the request thread, on the bounded agent pool. */
    void runInterview(String sessionId, SessionEmitter emitter) {
        InterviewSession session = store.find(sessionId).orElse(null);
        if (session == null) {
            log.error("session {} vanished between creation and start", sessionId);
            emitter.complete();
            return;
        }
        try {
            agent.run(session, new SseAgentEvents(emitter, metrics));
        } catch (RuntimeException e) {
            // The loop is built not to throw. If it does, the candidate still gets a clean end
            // rather than a stream that stops without explanation.
            log.error("session {} failed", sessionId, e);
            session.setState(SessionState.FAILED);
            emitter.send("done", java.util.Map.of("state", "FAILED"));
        } finally {
            finish(session, emitter);
        }
    }

    /**
     * Append a candidate answer.
     *
     * <p>Wrapped in delimiters at the point it enters the conversation: transcribed speech is the
     * one input the system cannot vet, and it arrives on every single turn.
     */
    public boolean submitAnswer(String sessionId, String turnId, String transcript,
                                Double sttConfidence) {
        InterviewSession session = store.find(sessionId).orElse(null);
        if (session == null || session.isTerminal()) {
            return false;
        }
        // A blank transcript is a failed speech-to-text, not an answer. Accepting it would put an
        // empty ANSWER turn in the transcript and the candidate would be graded on nothing - and
        // the grader has no way to tell "said nothing" from "the microphone failed". Rejecting
        // lets the client re-record, which is the only recovery that helps the candidate.
        if (transcript == null || transcript.isBlank()) {
            log.info("blank transcript for session {} turn {}, refused", sessionId, turnId);
            return false;
        }
        // Idempotency: a retried POST for the same turn must not be counted twice.
        if (!store.lockTurn(sessionId, turnId)) {
            log.info("duplicate answer for session {} turn {}, ignored", sessionId, turnId);
            return true;
        }
        synchronized (session) {
            var turn = session.addTurn(TurnKind.ANSWER, null, transcript, clock.instant());
            turn.setSttConfidence(sttConfidence);
            session.getMessages().add(ChatMessage.user(promptBuilder.wrapAnswer(transcript)));
            store.save(session);
        }
        return true;
    }

    /**
     * Which interview this candidate's job is configured for.
     *
     * <p>The client asks before deciding which page to open. Routing on the server's answer rather
     * than on a build flag is what makes {@code job.interview_mode} a real switch: flipping one
     * column moves a job onto the agent path, and flipping it back is the rollback. Nothing has to
     * be rebuilt or redeployed to undo it.
     *
     * <p>Unknown candidate returns {@code scripted}. The failure mode of guessing wrong in that
     * direction is a familiar interview; guessing the other way would send someone into a path
     * their job was never configured for.
     */
    public String interviewMode(String candidateId) {
        Candidate candidate = candidateService.getDetail(candidateId);
        if (candidate == null) {
            return "scripted";
        }
        Job job = jobService.getDetail(candidate.getJobId());
        return job == null || job.getInterviewMode() == null || job.getInterviewMode().isBlank()
                ? "scripted"
                : job.getInterviewMode();
    }

    /**
     * Start an interview for a client that cannot hold a stream open.
     *
     * <p>{@code EventSource} exists in browsers. The production client is a uni-app build targeting
     * app-plus, mp-weixin and h5, and only the last of those has it — so without this the agent
     * loop stays reachable from the eval harness and from nothing a candidate would ever use.
     *
     * <p>The session is created exactly as {@link #start} creates it, emitter included. That looks
     * wasteful and is deliberate: {@link SessionEmitter} already treats a client that is not there
     * as normal, because a candidate whose phone locks must not kill their interview. Reusing that
     * path means polling and streaming share one lifecycle rather than two that drift.
     *
     * @return the session id, or empty for an unknown candidate or one already interviewing
     */
    public Optional<String> startPolling(String candidateId) {
        return start(candidateId).map(SessionEmitter::sessionId);
    }

    /**
     * The question the candidate is currently expected to answer.
     *
     * <p>Computed from session state, not replayed from an event log. A queue of pending events
     * beside the stream would be a second delivery mechanism to keep in step with the first, and
     * the two would diverge the first time an event was added to one. The pending question is the
     * last QUESTION or FOLLOWUP turn with no ANSWER after it, which both transports can agree on
     * because it is a fact about the transcript rather than about delivery.
     *
     * <p>{@code afterSeq} is what the client already has. Returning a question it is holding would
     * make it ask the same thing twice on every poll.
     *
     * <p>Note what is not returned: the turn's kind. The SSE payload omits it for the same reason —
     * telling a candidate "this one is a follow-up" shows them the interviewer judged their last
     * answer weak enough to probe.
     */
    public PolledQuestion poll(String sessionId, int afterSeq) {
        InterviewSession session = store.find(sessionId).orElse(null);
        if (session == null) {
            // Sessions are deleted from the store once persisted, so "gone" and "finished" are the
            // same observation from here. A client that polls once more after the last answer must
            // be told the interview ended, not handed an error.
            return PolledQuestion.finished("FINISHED");
        }
        String state = String.valueOf(session.getState());
        synchronized (session) {
            Turn pending = pendingQuestion(session);
            if (pending != null && pending.getSeq() > afterSeq) {
                return new PolledQuestion(state, String.valueOf(pending.getSeq()),
                        pending.getSeq(), pending.getText(), pending.getAiSrc(), false);
            }
            return session.isTerminal()
                    ? PolledQuestion.finished(state)
                    : PolledQuestion.waiting(state);
        }
    }

    /**
     * The last question with nothing answering it.
     *
     * <p>Walked backwards: the first ANSWER encountered means every earlier question has been
     * dealt with, and CLOSING means the interview is over and there is nothing to answer.
     */
    private Turn pendingQuestion(InterviewSession session) {
        var turns = session.getTurns();
        for (int i = turns.size() - 1; i >= 0; i--) {
            Turn turn = turns.get(i);
            if (turn.getKind() == TurnKind.ANSWER || turn.getKind() == TurnKind.CLOSING) {
                return null;
            }
            if (turn.getKind() == TurnKind.QUESTION || turn.getKind() == TurnKind.FOLLOWUP) {
                return turn;
            }
        }
        return null;
    }

    private void finish(InterviewSession session, SessionEmitter emitter) {
        try {
            persister.persist(session);
        } catch (RuntimeException e) {
            log.error("could not persist session {}", session.getSessionId(), e);
        }
        metrics.sessionFinished(session);
        store.releaseCandidate(session.getCandidateId());
        store.delete(session.getSessionId());
        emitters.complete(session.getSessionId());
    }
}
