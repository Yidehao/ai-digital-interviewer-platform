package org.interviewer.agent.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One candidate's SSE connection.
 *
 * <p>Wraps {@link SseEmitter} so the rest of the system never touches a servlet type, and so the
 * two things that go wrong with SSE are handled in one place rather than at every call site:
 *
 * <ul>
 *   <li><b>The client disappears.</b> A phone locking its screen or a tab closing produces a broken
 *       pipe on the next send, not a callback. Every send is therefore guarded and a failure closes
 *       the emitter exactly once — an interview must not die because nobody is watching it.</li>
 *   <li><b>Sending after completion.</b> {@code SseEmitter} throws if used after {@code complete()},
 *       and the agent loop has no idea the connection ended. {@code closed} makes a late send a
 *       no-op instead of an exception that would surface as a tool failure.</li>
 * </ul>
 */
@Slf4j
public class SessionEmitter {

    private final String sessionId;
    private final SseEmitter emitter;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public SessionEmitter(String sessionId, SseEmitter emitter) {
        this.sessionId = sessionId;
        this.emitter = emitter;
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(e -> closed.set(true));
    }

    /** Named SSE event. Silently ignored once the client has gone. */
    public void send(String event, Object data) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            // Expected whenever a candidate closes the tab. Debug, not warn: this is normal, and a
            // log at warning level would fill with it.
            log.debug("session {} emitter closed while sending {}: {}",
                    sessionId, event, e.getMessage());
            closeQuietly();
        }
    }

    public void complete() {
        if (closed.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (RuntimeException e) {
                log.debug("session {} completed on an already-dead emitter", sessionId);
            }
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    public SseEmitter raw() {
        return emitter;
    }

    private void closeQuietly() {
        closed.set(true);
    }

    public String sessionId() {
        return sessionId;
    }
}
