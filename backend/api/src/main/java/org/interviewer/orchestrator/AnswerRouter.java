package org.interviewer.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.interviewer.agent.session.SessionStore;
import org.interviewer.utils.NodeIdentity;
import org.interviewer.utils.RedisOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Delivers an answer to the node actually running the interview.
 *
 * <p><b>Why this is necessary, and why the obvious alternatives are not enough.</b> Two things here
 * cannot be shared between nodes. The SSE emitter is a TCP socket, which exists on exactly one
 * machine. The live {@code InterviewSession} is the object the loop is mutating, and an object in
 * Redis is a snapshot — writing to a snapshot is precisely the bug that made answers invisible
 * <em>within</em> one node, where {@code SessionStore.find} deserialised a fresh copy on every call
 * and {@code submitAnswer} appended to something the interview had never seen.
 *
 * <p>Behind a load balancer without sticky sessions, that same bug reappears one layer up: the
 * candidate's stream is on node A, their {@code POST /answer} lands on node B, and node B writes the
 * answer to Redis where the interview will never look. The POST returns {@code success:true}, which
 * is the detail that makes it hard to spot.
 *
 * <p>Sticky sessions would fix it and are a real deployment answer. They are also a constraint on
 * infrastructure that this application can remove for itself: the <em>knowledge of where</em> a
 * session lives is shareable even though the session is not.
 *
 * <p><b>At-most-once delivery, chosen deliberately.</b> Redis pub/sub drops a message with no live
 * subscriber, so if the owning node dies between publish and receive the answer is lost and that
 * candidate's answer-gate times out after five minutes, ending the interview cleanly with
 * {@code CANDIDATE_TIMEOUT}. A durable queue would avoid the loss and introduce something worse: an
 * answer delivered after the interview finished, appended to a transcript that has already been
 * graded. Losing an answer is recoverable by the candidate re-recording. Corrupting a graded
 * transcript is not.
 */
@Slf4j
@Component
public class AnswerRouter {

    /** One channel per node, so a message is not fanned out to every process to be filtered. */
    public static final String CHANNEL_PREFIX = "interview_answer:";

    private final RedisOperator redis;
    private final SessionStore store;
    private final ObjectMapper objectMapper;
    private final NodeIdentity nodeIdentity;

    /**
     * {@code ObjectProvider} breaks a constructor cycle: the orchestrator needs the router to
     * forward, and the router needs the orchestrator to apply what it receives. Resolving lazily is
     * honest about the cycle rather than hiding it behind a setter.
     */
    private final ObjectProvider<InterviewOrchestrator> orchestrator;

    public AnswerRouter(RedisOperator redis,
                        SessionStore store,
                        ObjectMapper objectMapper,
                        NodeIdentity nodeIdentity,
                        ObjectProvider<InterviewOrchestrator> orchestrator) {
        this.redis = redis;
        this.store = store;
        this.objectMapper = objectMapper;
        this.nodeIdentity = nodeIdentity;
        this.orchestrator = orchestrator;
    }

    public String channel() {
        return CHANNEL_PREFIX + nodeIdentity.id();
    }

    /**
     * Forward an answer to whichever node owns the session.
     *
     * @return false when nothing owns it — which means the interview is over, not that delivery
     *         failed. Telling the client "accepted" for a finished interview would leave them
     *         waiting for a next question that is never coming.
     */
    public boolean forward(String sessionId, String turnId, String transcript,
                           Double sttConfidence) {
        String owner = store.ownerOf(sessionId);
        if (owner == null) {
            log.info("answer for session {} has no owning node, refusing", sessionId);
            return false;
        }
        if (owner.equals(nodeIdentity.id())) {
            // Owned here after all - the live map lost it between the two checks, which means the
            // session finished mid-request. Same outcome as no owner.
            return false;
        }
        try {
            String payload = objectMapper.writeValueAsString(
                    new ForwardedAnswer(sessionId, turnId, transcript, sttConfidence));
            redis.publish(CHANNEL_PREFIX + owner, payload);
            log.info("forwarded answer for session {} to node {}", sessionId, owner);
            return true;
        } catch (Exception e) {
            log.error("could not forward answer for session {} to node {}", sessionId, owner, e);
            return false;
        }
    }

    /** Called by the subscriber when another node forwards us an answer. */
    public void receive(String payload) {
        try {
            ForwardedAnswer answer = objectMapper.readValue(payload, ForwardedAnswer.class);
            boolean applied = orchestrator.getObject().applyAnswer(
                    answer.sessionId(), answer.turnId(), answer.transcript(),
                    answer.sttConfidence());
            if (!applied) {
                // The candidate was already told the answer was accepted. Nothing can be done for
                // them now, so it is logged loudly enough to be found rather than swallowed.
                log.warn("forwarded answer for session {} could not be applied", answer.sessionId());
            }
        } catch (Exception e) {
            log.error("could not apply forwarded answer: {}", payload, e);
        }
    }

    public record ForwardedAnswer(String sessionId, String turnId, String transcript,
                                  Double sttConfidence) {
    }
}
