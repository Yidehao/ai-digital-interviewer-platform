# Load test — app tier, model stubbed

**Headline: the app tier did not break at any level tested.** The highest clean concurrency was, at
every pool size, exactly the configured admission bound — threads + queue. The number is a statement
about configuration, not about the machine.

Apple M1, 16 GB. `dev,loadtest` profile: model stubbed, `NoWaitCandidateGate` active (no session ever
waits for a candidate), one node.

| threads | queue | admission cap | max clean | sess/s at cap | first-event p50 / p95 | session p50 |
|---|---|---|---|---|---|---|
| 32 | 96 | 128 | **128** | 60 | 0.087 / 0.124 s | 1.56 s |
| 64 | 192 | 256 | **256** | 115 | 0.121 / 0.182 s | 1.36 s |
| 128 | 384 | 512 | **512** | 205 | 0.213 / 0.323 s | 1.54 s |

Throughput scales linearly with thread count; latency stays flat. Beyond the bound, excess sessions
are refused immediately and the admitted ones are unaffected — at 600 concurrent against a 512 bound,
512 completed at 205 sessions/s while 88 were refused.

**What was never the constraint:** CPU held between 5% and 20%, and HikariCP `connections.pending`
never exceeded 1 against a 20-connection pool. Nothing in the app tier saturated.

## Three measurement bugs found before the numbers meant anything

Each of these produced a plausible-looking curve that was wrong.

**1. Two candidate rows.** One in-flight interview per candidate is enforced by design
(`claimCandidate`, a Redis SETNX). Driving 128 concurrent sessions against 2 candidates gives 2
sessions and 126 refusals, which reads exactly like a capacity ceiling. `seed_loadtest_candidates.py`
now creates one row per concurrent session.

**2. A rejection leak, and then a cleanup leak.** Both left the candidate claimed:

- `agentExecutor.execute` throws `RejectedExecutionException` under overload — by design — but by
  then the candidate was claimed, the session was in Redis and an emitter was registered, and none
  of it was undone. **A rejected candidate was locked out for the claim's full three-hour TTL.**
- `finish()` ran as a straight sequence with `verdicts.gradeLater()` *before* the releases. That call
  throws when the grading queue is full, so under load every finished session skipped its own
  cleanup. A sweep left **134 orphaned claims with both pools completely idle.**

The second one also flattered the benchmark: sessions that skipped cleanup finished faster, so
throughput appeared to jump from 15 to 56 sessions/s at exactly the concurrency where the grading
queue overflowed. A bug that makes the benchmark look better is the worst kind.

**3. Not enough JVM warmup.** A fresh JVM sustains ~15 sessions/s and stays there far longer than
feels plausible — two rounds of 48 was nowhere near enough. Steady state arrives somewhere around 700
sessions. The tell is a curve where **throughput rises with concurrency**: that is compilation
finishing mid-sweep, not headroom appearing. `--warmup-rounds` now defaults to 6 × 128.

## The real bottleneck is grading, not the interview

With every finished session actually graded, throughput settles at roughly half the thread count
(~31/s at 64 threads). With the grading queue saturated and grades shed, it reaches ~100/s on the same
pool. `gradingExecutor` is 2 threads by design, because grading is one model call — measured at 91 s
against the local 7B.

**That is the number that matters for capacity planning, and it is not the interview loop.** Two
threads at ~91 s per grade is about 0.02 grades/s, so any sustained interview rate above roughly one
per minute builds an unbounded backlog. Interviews are cheap; grading them is not.

## Reproducing

```bash
python3 eval/seed_loadtest_candidates.py --count 600     # one row per concurrent session
cd backend && mvn -pl api spring-boot:run -Dspring-boot.run.profiles=dev,loadtest
python3 eval/loadtest.py --levels 128,256,384,512,600 --candidates "$(cat ids.txt)"
python3 eval/seed_loadtest_candidates.py --delete        # afterwards
```
