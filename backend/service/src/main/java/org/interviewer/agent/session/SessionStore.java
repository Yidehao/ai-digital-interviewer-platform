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
 */
@Slf4j
@Component
public class SessionStore extends BaseInfoProperties {

    private final ObjectMapper objectMapper;

    public SessionStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void save(InterviewSession session) {
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
        redis.del(key(sessionId));
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
                INTERVIEW_SESSION_TTL_SECONDS));
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
