package org.interviewer.utils;

import org.slf4j.MDC;

import java.util.Map;

/**
 * Correlation keys carried in the logging context.
 *
 * <p>An interview produces log lines from at least three threads — the request thread, the agent
 * pool thread running the loop, and a tool-executor thread — and by default nothing ties them
 * together. When something goes wrong in production the first question is always "show me
 * everything that happened in that one interview", and without these keys the answer involves
 * guessing from timestamps.
 *
 * <p>The subtle part is {@link #capture()} / {@link #restore}. MDC is thread-local, so it does
 * <em>not</em> propagate when work is handed to an executor — the loop's own log lines would lose
 * the session id at exactly the moment they became interesting. Capturing on the submitting thread
 * and restoring on the worker is what keeps the trail intact across the hand-off.
 */
public final class MdcKeys {

    public static final String SESSION_ID = "sessionId";
    public static final String CANDIDATE_ID = "candidateId";
    public static final String REQUEST_ID = "requestId";
    public static final String TOOL = "tool";

    public static void putSession(String sessionId, String candidateId) {
        if (sessionId != null) {
            MDC.put(SESSION_ID, sessionId);
        }
        if (candidateId != null) {
            MDC.put(CANDIDATE_ID, candidateId);
        }
    }

    /** Snapshot for handing to another thread. Null-safe: an empty context is legal. */
    public static Map<String, String> capture() {
        return MDC.getCopyOfContextMap();
    }

    public static void restore(Map<String, String> context) {
        MDC.clear();
        if (context != null) {
            MDC.setContextMap(context);
        }
    }

    public static void clear() {
        MDC.clear();
    }

    /**
     * Wraps a task so it runs with the submitting thread's context.
     *
     * <p>Always clears afterwards. Pool threads are reused, and a leaked session id would attach
     * the previous interview's identity to the next one's log lines — worse than no correlation at
     * all, because it is confidently wrong.
     */
    public static Runnable wrap(Runnable task) {
        Map<String, String> context = capture();
        return () -> {
            restore(context);
            try {
                task.run();
            } finally {
                clear();
            }
        };
    }

    private MdcKeys() {
    }
}
