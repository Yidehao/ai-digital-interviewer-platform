package org.interviewer.agent.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.interviewer.base.BaseInfoProperties;
import org.interviewer.entity.agent.InterviewSession;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The hot copy of a session, in Redis.
 *
 * <p>Uses the Spring-managed {@link ObjectMapper} rather than the project's {@code JsonUtils}.
 * That is not a style preference: {@code JsonUtils} swallows exceptions and returns null, so a
 * session that failed to serialise would be dropped <em>silently</em> — the interview would appear
 * to continue while its state quietly stopped being saved. Here a failure throws, and the caller
 * finds out.
 *
 * <p>TTL matches {@code REDIS_USER_TOKEN}'s three hours. A session should not outlive the login
 * that created it.
 *
 * <h2>Why there is also an in-memory map</h2>
 *
 * <p>Redis alone was <b>wrong</b>, and wrong in a way that looked like it worked. {@link #find}
 * deserialises JSON, so every call returned a <em>different object</em>. The loop held one instance
 * for the whole interview while {@code submitAnswer} appended the candidate's answer to a
 * short-lived copy, saved it, and returned {@code success:true}. The answer reached Redis and never
 * reached the interview. Worse, {@code synchronized(session)} in the two places locked different
 * objects, so the mutual exclusion the orchestrator documents did not exist and a {@code notifyAll}
 * woke a monitor nobody was waiting on.
 *
 * <p>Live sessions are therefore kept as one object per node, and Redis holds a snapshot for
 * persistence. That is not a new constraint: {@code EmitterRegistry} is already an in-memory map,
 * so this deployment already requires sticky sessions. What changes is that the requirement is now
 * stated in the one place that would otherwise silently drop data.
 */
@Slf4j
@Component
public class SessionStore extends BaseInfoProperties {

    private final ObjectMapper objectMapper;

    /**
     * The one instance of each in-flight session on this node.
     *
     * <p>Bounded by concurrent interviews rather than by time: {@link #delete} runs in the
     * orchestrator's {@code finally}, so a session leaves this map whether it finished, failed or
     * timed out.
     */
    private final java.util.Map<String, InterviewSession> live = new java.util.concurrent.ConcurrentHashMap<>();

    public SessionStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** True when the interview loop for this session is running in THIS process. */
    public boolean isOwnedHere(String sessionId) {
        return live.containsKey(sessionId);
    }

    /**
     * Which node is running this interview, or null if nobody is.
     *
     * <p>Written when the session is saved and deleted with it, so it cannot outlive the thing it
     * points at. An answer that arrives for a session with no owner is an answer for an interview
     * that has already ended.
     */
    public String ownerOf(String sessionId) {
        return redis.get(ownerKey(sessionId));
    }

    public void claimOwnership(String sessionId, String nodeId) {
        redis.set(ownerKey(sessionId), nodeId, INTERVIEW_SESSION_TTL_SECONDS);
    }

    private String ownerKey(String sessionId) {
        return "redis_interview_owner:" + sessionId;
    }

    public void save(InterviewSession session) {
        live.put(session.getSessionId(), session);
        try {
            redis.set(key(session.getSessionId()),
                    objectMapper.writeValueAsString(session),
                    INTERVIEW_SESSION_TTL_SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "could not persist session " + session.getSessionId(), e);
        }
    }

    public Optional<InterviewSession> find(String sessionId) {
        // The live object first. Returning a fresh deserialisation while the loop is running would
        // hand the caller a snapshot, and mutations to it - an answer, most importantly - would be
        // written to Redis and never seen by the interview.
        InterviewSession inFlight = live.get(sessionId);
        if (inFlight != null) {
            return Optional.of(inFlight);
        }
        String json = redis.get(key(sessionId));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, InterviewSession.class));
        } catch (Exception e) {
            // A session we cannot read is worse than one we do not have: the caller would carry on
            // with a half-restored object. Report it and let them start cleanly.
            log.error("session {} is in Redis but unreadable", sessionId, e);
            return Optional.empty();
        }
    }

    public void delete(String sessionId) {
        live.remove(sessionId);
        redis.del(ownerKey(sessionId));
        redis.del(key(sessionId));
    }

    /** Live sessions on this node. Exposed for the gauge, which is how a leak would be noticed. */
    public int liveCount() {
        return live.size();
    }

    /**
     * Claim the one in-flight session a candidate is allowed.
     *
     * @return false when they already have one — which is what stops a double-tap on "start"
     *         producing two interviews for the same person
     */
    public boolean claimCandidate(String candidateId, String sessionId) {
        return Boolean.TRUE.equals(redis.setnx(
                REDIS_INTERVIEW_CANDIDATE + ":" + candidateId, sessionId,
                CANDIDATE_CLAIM_TTL_SECONDS));
    }

    /**
     * Release every claim this node holds, on the way down.
     *
     * <p>Without this, killing the process strands its claims for the full TTL, and recovery means
     * deleting keys from Redis by hand — which no candidate can do and no support process was
     * written for. A restart during an interview should cost the candidate a retry, not their
     * afternoon.
     *
     * <p>Best effort by construction. A SIGKILL runs no hooks at all, which is exactly why the TTL
     * was also shortened: the hook handles the ordinary case and the TTL bounds the worst one.
     */
    @jakarta.annotation.PreDestroy
    public void releaseAllOnShutdown() {
        int released = 0;
        for (InterviewSession session : live.values()) {
            try {
                redis.del(REDIS_INTERVIEW_CANDIDATE + ":" + session.getCandidateId());
                released++;
            } catch (RuntimeException e) {
                log.warn("could not release claim for candidate {} during shutdown",
                        session.getCandidateId(), e);
            }
        }
        if (released > 0) {
            log.info("released {} candidate claims during shutdown", released);
        }
    }

    public void releaseCandidate(String candidateId) {
        redis.del(REDIS_INTERVIEW_CANDIDATE + ":" + candidateId);
    }

    /**
     * Idempotency for answer submission: a retried POST for the same turn must not be processed
     * twice. Sixty seconds is long enough to cover a client retry and short enough not to wedge a
     * session if something dies mid-turn.
     */
    public boolean lockTurn(String sessionId, String turnId) {
        return Boolean.TRUE.equals(
                redis.setnx60s(REDIS_INTERVIEW_TURN_LOCK + ":" + sessionId + ":" + turnId, "1"));
    }

    private String key(String sessionId) {
        return REDIS_INTERVIEW_SESSION + ":" + sessionId;
    }
}
