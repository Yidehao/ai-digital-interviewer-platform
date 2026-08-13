package org.interviewer.agent.sandbox;

import org.interviewer.agent.tool.dto.RunCodeResult;
import org.interviewer.utils.SandboxProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The sandbox, against real containers.
 *
 * <p>Tagged {@code docker} and excluded from the default build: these take seconds rather than
 * milliseconds and need a working daemon. Run them with {@code -Dgroups=docker}.
 *
 * <p>They are worth the seconds. This is the only component that executes code written by someone
 * else, and every assertion here is a containment property that would be a security bug if it
 * regressed — not "does it print the right number", but "can it reach the network", "can it write
 * to disk", "can it survive being told to stop". A sandbox nobody has attacked is a sandbox nobody
 * knows the shape of.
 */
@Tag("docker")
class DockerCodeRunnerTest {

    private static DockerCodeRunner runner;

    @BeforeAll
    static void startSandbox() {
        SandboxProperties properties = new SandboxProperties();
        runner = new DockerCodeRunner(properties);
        runner.pullImages();
        assumeTrue(runner.isAvailable(), "docker unavailable - skipping sandbox tests");
    }

    @Test
    @DisplayName("runs ordinary code and returns its output")
    void runsPython() {
        RunCodeResult result = runner.run("python", """
                def fib(n):
                    a, b = 0, 1
                    for _ in range(n):
                        a, b = b, a + b
                    return a

                print(fib(10))
                """, "", 5_000);

        assertThat(result.stdout().trim()).isEqualTo("55");
        assertThat(result.exitCode()).isZero();
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    @DisplayName("javascript works too")
    void runsJavaScript() {
        RunCodeResult result = runner.run("javascript",
                "console.log([1,2,3].reduce((a,b)=>a+b,0));", "", 5_000);

        assertThat(result.stdout().trim()).isEqualTo("6");
        assertThat(result.exitCode()).isZero();
    }

    @Test
    @DisplayName("stdin reaches the program")
    void stdinIsDelivered() {
        RunCodeResult result = runner.run("python",
                "import sys; print(sys.stdin.read().strip().upper())", "hello", 5_000);

        assertThat(result.stdout().trim()).isEqualTo("HELLO");
    }

    @Test
    @DisplayName("a crash is a normal result, not an exception")
    void aCrashIsReportedAsData() {
        RunCodeResult result = runner.run("python", "raise ValueError('boom')", "", 5_000);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("ValueError");
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    @DisplayName("an infinite loop is killed and reported as exitCode 124, timedOut true")
    void infiniteLoopIsKilled() {
        long started = System.currentTimeMillis();
        RunCodeResult result = runner.run("python", "while True: pass", "", 2_000);
        long elapsed = System.currentTimeMillis() - started;

        assertThat(result.timedOut()).isTrue();
        assertThat(result.exitCode()).isEqualTo(124);
        // Code that hangs is information about the candidate, not a system fault.
        assertThat(elapsed).isLessThan(15_000L);
    }

    @Test
    @DisplayName("the network is unreachable")
    void networkIsBlocked() {
        RunCodeResult result = runner.run("python", """
                import socket
                try:
                    socket.create_connection(("1.1.1.1", 53), timeout=3)
                    print("REACHED")
                except Exception as e:
                    print("BLOCKED")
                """, "", 8_000);

        // Without --network=none, dictated code could exfiltrate anything it can read.
        assertThat(result.stdout()).contains("BLOCKED");
        assertThat(result.stdout()).doesNotContain("REACHED");
    }

    @Test
    @DisplayName("the filesystem is read-only outside the scratch tmpfs")
    void filesystemIsReadOnly() {
        RunCodeResult result = runner.run("python", """
                try:
                    open("/etc/passwd", "a").write("x")
                    print("WROTE")
                except Exception:
                    print("REFUSED")
                """, "", 5_000);

        assertThat(result.stdout()).contains("REFUSED");
    }

    @Test
    @DisplayName("the mounted code directory cannot be written to either")
    void codeMountIsReadOnly() {
        RunCodeResult result = runner.run("python", """
                try:
                    open("/code/evil.py", "w").write("x")
                    print("WROTE")
                except Exception:
                    print("REFUSED")
                """, "", 5_000);

        assertThat(result.stdout()).contains("REFUSED");
    }

    @Test
    @DisplayName("a memory bomb is OOM-killed inside the container, not on the host")
    void memoryIsCapped() {
        RunCodeResult result = runner.run("python",
                "x = bytearray(400 * 1024 * 1024); print('ALLOCATED')", "", 8_000);

        // 400 MB against a 256 MB cap. The container dies; the host does not start swapping.
        assertThat(result.stdout()).doesNotContain("ALLOCATED");
        assertThat(result.exitCode()).isNotZero();
    }

    @Test
    @DisplayName("runaway output is truncated rather than filling a log or a prompt")
    void outputIsTruncated() {
        RunCodeResult result = runner.run("python",
                "print('x' * 200000)", "", 8_000);

        assertThat(result.truncated()).isTrue();
        assertThat(result.stdout().length()).isLessThanOrEqualTo(4_000);
    }

    @Test
    @DisplayName("it runs as an unprivileged user")
    void runsAsNobody() {
        RunCodeResult result = runner.run("python",
                "import os; print(os.getuid())", "", 5_000);

        // 65534 is nobody. Even a container escape lands without privileges.
        assertThat(result.stdout().trim()).isEqualTo("65534");
    }

    @Test
    @DisplayName("an unsupported language is refused in the normal result shape")
    void unsupportedLanguageIsRefused() {
        RunCodeResult result = runner.run("cobol", "DISPLAY 'HI'.", "", 5_000);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("unsupported language");
        assertThat(result.timedOut()).isFalse();
    }
}
