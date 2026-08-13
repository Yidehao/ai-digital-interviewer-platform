package org.interviewer.agent.sandbox;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.interviewer.agent.tool.dto.RunCodeResult;
import org.interviewer.utils.SandboxProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Runs candidate code in a throwaway Docker container, one container per execution.
 *
 * <p><b>This is the only place in the system that executes code someone else wrote</b>, so the
 * flags below are the security boundary, not configuration. Each one refuses something specific:
 *
 * <ul>
 *   <li>{@code --network=none} — no egress at all. Without it, dictated code can exfiltrate
 *       anything it can read, and "it was only an interview question" is not a defence.</li>
 *   <li>{@code --read-only} plus a {@code noexec,nosuid} tmpfs — nothing can be written except
 *       scratch space that cannot then be executed.</li>
 *   <li>{@code --memory} equal to {@code --memory-swap} — swap is disabled, so a memory bomb is
 *       OOM-killed inside the container instead of dragging the host into swap.</li>
 *   <li>{@code --pids-limit} — a fork bomb hits a ceiling rather than the host's process table.</li>
 *   <li>{@code --cap-drop=ALL} and {@code --security-opt=no-new-privileges} — no capabilities, and
 *       no setuid binary can regain them.</li>
 *   <li>{@code -u 65534:65534} — runs as {@code nobody}, so even a container escape lands
 *       unprivileged.</li>
 *   <li>{@code --rm} — the container is gone afterwards whatever happened inside it.</li>
 * </ul>
 *
 * <p>Images are pulled at startup. A cold {@code docker pull} inside a three-second tool timeout
 * always fails, and it would fail as a timeout — indistinguishable from the candidate's code
 * hanging, which is the worst possible confusion for a signal we intend to grade on.
 *
 * <p>Container cold start is 200-600 ms, which is why {@code run_code} must never be on the path
 * whose latency is quoted.
 */
@Slf4j
@Component
public class DockerCodeRunner implements CodeRunner {

    private final SandboxProperties properties;
    private final Semaphore slots;
    private volatile boolean available;

    public DockerCodeRunner(SandboxProperties properties) {
        this.properties = properties;
        this.slots = new Semaphore(Math.max(1, properties.getMaxConcurrent()));
    }

    @PostConstruct
    void pullImages() {
        if (!properties.isEnabled()) {
            log.info("code sandbox disabled by configuration; run_code will report unavailable");
            return;
        }
        for (String image : properties.getImages().values()) {
            if (!pull(image)) {
                log.error("could not pull {} - run_code stays unavailable rather than failing "
                        + "mid-interview with a timeout that looks like the candidate's fault",
                        image);
                available = false;
                return;
            }
        }
        available = true;
        log.info("code sandbox ready: {}", properties.getImages());
    }

    private boolean pull(String image) {
        try {
            Process process = new ProcessBuilder("docker", "image", "inspect", image)
                    .redirectErrorStream(true).start();
            if (process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0) {
                return true;
            }
            log.info("pulling sandbox image {}", image);
            Process pull = new ProcessBuilder("docker", "pull", image)
                    .redirectErrorStream(true).start();
            boolean done = pull.waitFor(properties.getPullTimeoutSeconds(), TimeUnit.SECONDS);
            if (!done) {
                pull.destroyForcibly();
                return false;
            }
            return pull.exitValue() == 0;
        } catch (IOException e) {
            log.warn("docker is not usable: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public RunCodeResult run(String language, String source, String stdin, long timeoutMs) {
        if (!available) {
            return new RunCodeResult("", "code execution is unavailable on this deployment",
                    126, false, false, 0L);
        }
        String image = properties.getImages().get(language);
        if (image == null) {
            return new RunCodeResult("", "unsupported language: " + language, 126, false, false, 0L);
        }

        long budget = Math.min(timeoutMs, properties.getMaxTimeoutMs());
        Path work = null;
        boolean acquired = false;
        long started = System.nanoTime();
        try {
            // Bound concurrency: container start is real work, and an unbounded burst would
            // compete with the interview itself for CPU on a laptop-sized machine.
            acquired = slots.tryAcquire(budget, TimeUnit.MILLISECONDS);
            if (!acquired) {
                return new RunCodeResult("", "sandbox busy", 126, false, false,
                        millisSince(started));
            }

            work = Files.createTempDirectory("interviewer-run-");
            String fileName = "javascript".equals(language) ? "main.js" : "main.py";
            Path file = work.resolve(fileName);
            Files.writeString(file, source, StandardCharsets.UTF_8);

            return execute(image, language, work, fileName, stdin, budget, started);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RunCodeResult("", "interrupted", 126, false, false, millisSince(started));
        } catch (IOException e) {
            log.error("sandbox setup failed", e);
            return new RunCodeResult("", "sandbox setup failed", 126, false, false,
                    millisSince(started));
        } finally {
            if (acquired) {
                slots.release();
            }
            deleteQuietly(work);
        }
    }

    private RunCodeResult execute(String image,
                                  String language,
                                  Path work,
                                  String fileName,
                                  String stdin,
                                  long budget,
                                  long started) throws IOException, InterruptedException {

        List<String> command = new ArrayList<>(List.of(
                "docker", "run", "--rm", "-i",
                "--network=none",
                "--memory=256m", "--memory-swap=256m",
                "--cpus=0.5",
                "--pids-limit=64",
                "--read-only",
                "--tmpfs", "/tmp:rw,size=16m,noexec,nosuid",
                "--security-opt=no-new-privileges",
                "--cap-drop=ALL",
                "-u", "65534:65534",
                "-v", work.toAbsolutePath() + ":/code:ro",
                "-w", "/code",
                image));
        command.addAll("javascript".equals(language)
                ? List.of("node", "/code/" + fileName)
                : List.of("python", "/code/" + fileName));

        Process process = new ProcessBuilder(command).start();

        try (OutputStream in = process.getOutputStream()) {
            if (stdin != null && !stdin.isEmpty()) {
                in.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // The process can exit before reading stdin. That is the candidate's code's business.
        }

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread outReader = drain(process.getInputStream(), stdout);
        Thread errReader = drain(process.getErrorStream(), stderr);

        boolean finished = process.waitFor(budget, TimeUnit.MILLISECONDS);
        if (!finished) {
            // destroyForcibly, not destroy: the container must go away, and a polite signal to a
            // process that is already ignoring the clock is optimism.
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
            outReader.join(500);
            errReader.join(500);
            // 124 with timedOut is a normal result shape, not an error envelope. Code that hangs
            // is information about the candidate.
            return truncated(stdout, stderr, 124, true, millisSince(started));
        }
        outReader.join(1_000);
        errReader.join(1_000);
        return truncated(stdout, stderr, process.exitValue(), false, millisSince(started));
    }

    private RunCodeResult truncated(StringBuilder stdout, StringBuilder stderr,
                                    int exitCode, boolean timedOut, long durationMs) {
        int cap = properties.getMaxOutputChars();
        boolean cut = stdout.length() > cap || stderr.length() > cap;
        return new RunCodeResult(
                stdout.length() > cap ? stdout.substring(0, cap) : stdout.toString(),
                stderr.length() > cap ? stderr.substring(0, cap) : stderr.toString(),
                exitCode, timedOut, cut, durationMs);
    }

    /**
     * Drains a stream on its own thread. Without this a container that fills the pipe buffer blocks
     * forever and the timeout never fires - the classic {@code ProcessBuilder} deadlock.
     */
    private Thread drain(InputStream stream, StringBuilder sink) {
        Thread thread = new Thread(() -> {
            int cap = properties.getMaxOutputChars() * 2;
            try (InputStream in = stream) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1 && sink.length() < cap) {
                    sink.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // Stream closed by destroyForcibly. Whatever we have is what we report.
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private long millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort; the temp directory is cleaned by the OS eventually.
                }
            });
        } catch (IOException ignored) {
            // Same.
        }
    }

    @PreDestroy
    void shutdown() {
        available = false;
    }
}
