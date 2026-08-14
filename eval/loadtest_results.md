# Phase 8 — load test results

**App tier, model stubbed, per node.** Apple M1, 8 cores, 16 GB. `dev,loadtest` profile.

## The curve

| concurrent | completed | rejected | first event p50 | total p50 | sessions/s |
|---|---|---|---|---|---|
| 32 | 32 | 0 | 0.019 s | 0.96 s | 30.2 |
| 40 | 40 | 0 | 0.022 s | 0.98 s | 26.5 |
| **48** | **48** | **0** | **0.025 s** | **1.00 s** | **31.5** |
| 56 | 48 | 8 | 0.030 s | 1.01 s | 31.2 |
| 64 | 48 | 16 | 0.029 s | 1.01 s | 30.9 |

## What the number means

**48 concurrent SSE interview sessions per node.** That is not a hardware limit — it is
`agentExecutor`'s deliberate bound: 16 threads plus a 32-slot queue, with `AbortPolicy`. The
measurement matches the configuration exactly, and beyond it precisely the excess is refused.

**Rejection is clean, not degraded.** Latency is flat across the knee: first event stays ~0.03 s
and total ~1.0 s at 64 concurrent, where 16 sessions are being turned away. The system does not
get slower under overload; it declines work. `CallerRunsPolicy` would have produced the opposite —
interviews running on Tomcat threads, the app looking healthy while its request threads drained.

**The app tier was not saturated at 48.** Throughput held at ~31 sessions/s and latency did not
move, so the ceiling is the configured bound rather than CPU or memory. Raising `maxPoolSize` and
the queue would raise it; whether that is wise depends on what the model tier can actually feed.

## What this is not

- **Not end-to-end capacity.** The model is stubbed at 50 ms. One 7B model on one GPU serialises,
  so a real-model curve would flatten at 1–2 sessions and describe Ollama rather than this system.
- **Not horizontal.** `EmitterRegistry` is an in-memory map because `RedisOperator` has no pub/sub.
  A second node shares nothing, and a load balancer would need sticky sessions.
- **Not measured with logging on.** `mybatis-plus log-impl` is `Slf4jImpl` here. Left at the global
  `StdOutImpl`, this would have measured `System.out` contention.

## The bug in the first version of this harness

The first run reported **114 concurrent with zero failures**. It was wrong. The harness counted any
stream that produced events as a success, and a rejected session sends one event then closes — so
rejections scored as successes. The server's own metrics gave it away: 169 sessions finished out of
272 attempted.

Fixed by requiring `event:done`. **A load test that scores rejections as successes measures
nothing**, and the failure mode is silent — it produces a large, flattering, false number.
