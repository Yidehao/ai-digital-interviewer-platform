package org.interviewer.agent.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live SSE connections, by session.
 *
 * <p><b>Node-local, and that is a real deployment constraint rather than a simplification.</b> The
 * project's {@code RedisOperator} has no pub/sub, so an emitter registered on one node cannot be
 * reached from another. Two consequences worth stating out loud rather than discovering in
 * production:
 *
 * <ul>
 *   <li>A load balancer in front of this needs sticky sessions. A candidate whose {@code /answer}
 *       POST lands on a different node than their {@code /stream} connection gets a stream that
 *       goes silent while the interview proceeds without them.</li>
 *   <li>Any concurrency figure quoted from a load test is <b>per node</b>. Saying otherwise would
 *       be claiming horizontal scale this does not have.</li>
 * </ul>
 */
@Slf4j
@Component
public class EmitterRegistry {

    private final Map<String, SessionEmitter> emitters = new ConcurrentHashMap<>();

    public SessionEmitter register(String sessionId, long timeoutMillis) {
        SseEmitter raw = new SseEmitter(timeoutMillis);
        SessionEmitter emitter = new SessionEmitter(sessionId, raw);

        // Remove on every terminal path, or a long-running process accumulates dead emitters and
        // the map becomes a slow leak that only shows up under load.
        raw.onCompletion(() -> emitters.remove(sessionId));
        raw.onTimeout(() -> emitters.remove(sessionId));
        raw.onError(e -> emitters.remove(sessionId));

        SessionEmitter previous = emitters.put(sessionId, emitter);
        if (previous != null) {
            // A reconnect. The old connection is already dead or about to be; completing it stops
            // the server holding a socket nobody is reading.
            previous.complete();
        }
        log.debug("registered emitter for session {} ({} live)", sessionId, emitters.size());
        return emitter;
    }

    public Optional<SessionEmitter> find(String sessionId) {
        return Optional.ofNullable(emitters.get(sessionId));
    }

    public void complete(String sessionId) {
        SessionEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            emitter.complete();
        }
    }

    /** For the actuator gauge: how many candidates are connected to this node right now. */
    public int liveCount() {
        return emitters.size();
    }
}
