package org.interviewer.utils;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How {@code run_code} executes candidate-dictated code.
 *
 * <p>This is the only place in the system that runs code someone else wrote, so the defaults are
 * the security boundary rather than a tuning knob. Every one of them is a deliberate refusal:
 * no network, no writable filesystem, no privilege escalation, no capabilities, an unprivileged
 * uid, a pid cap so a fork bomb cannot exhaust the host, and a memory cap equal to the swap cap so
 * the container is OOM-killed rather than pushing the host into swap.
 */
@Data
@Component
@ConfigurationProperties(prefix = "interviewer.sandbox")
public class SandboxProperties {

    private boolean enabled = true;

    /** Wall-clock ceiling the runner enforces, independent of the model's requested timeout. */
    private long maxTimeoutMs = 5_000L;

    /** stdout and stderr are truncated here. A runaway printer must not fill a log or a prompt. */
    private int maxOutputChars = 4_000;

    /**
     * Concurrent containers. Guarded by a semaphore because container start is 200-600 ms of real
     * work and an unbounded burst would compete with the interview itself for CPU.
     */
    private int maxConcurrent = 2;

    /** Seconds to wait for a `docker pull` at startup before giving up and disabling the tool. */
    private long pullTimeoutSeconds = 300;

    /**
     * Images per language. Slim variants on purpose: a cold pull inside a 3 s tool timeout always
     * fails, so these are pulled at startup, and pull time is proportional to size.
     */
    private Map<String, String> images = new LinkedHashMap<>(Map.of(
            "python", "python:3.12-slim",
            "javascript", "node:22-slim"));
}
