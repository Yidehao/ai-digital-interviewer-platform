#!/usr/bin/env python3
"""
Phase 8 - how many concurrent interview sessions does one node sustain?

WHAT IS BEING MEASURED, AND WHAT IS NOT

  The model is stubbed. That is not a shortcut - it is the only way to get a number that means
  anything. With the real model, one 7B instance on one GPU serialises requests, so the curve
  flattens at 1-2 sessions and describes Ollama rather than this application. What is worth knowing
  is what the APP TIER sustains: SSE emitters, the three thread pools, tool dispatch, Redis, MySQL.
  That is the part that was built here and the part that would scale differently.

  So every number this produces is "app tier, model stubbed" and must be reported that way. Quoting
  it as end-to-end capacity would claim throughput the GPU cannot deliver.

  It is also PER NODE. EmitterRegistry is an in-memory map because RedisOperator has no pub/sub, so
  a second node shares nothing. Any figure implying horizontal scale would be false.

WHY NOT k6

  k6 is the conventional tool and the plan named it. A Python asyncio client measures the same
  thing here - open N concurrent SSE streams, record time to first event and completion - without
  adding an install to a machine that has been short on disk all week. If this needs to run in CI
  later, k6 is the better home for it.

    python3 eval/loadtest.py                    # the full curve
    python3 eval/loadtest.py --levels 1,10,50   # specific levels
"""

import argparse
import asyncio
import json
import statistics
import sys
import time
from pathlib import Path

HERE = Path(__file__).parent


async def one_session(base_url, candidate_id, timeout):
    """
    Open a stream, read until `done`, and report timings.

    Returns (time_to_first_event, total_seconds, events, error_or_None).
    """
    started = time.perf_counter()
    first_event = None
    events = 0
    saw_done = False
    reader = writer = None
    try:
        host, port = base_url.split("://")[1].split(":")
        reader, writer = await asyncio.wait_for(
            asyncio.open_connection(host, int(port)), timeout=10)

        request = (f"GET /interview/{candidate_id}/stream HTTP/1.1\r\n"
                   f"Host: {host}:{port}\r\n"
                   f"Accept: text/event-stream\r\n"
                   f"Connection: close\r\n\r\n")
        writer.write(request.encode())
        await writer.drain()

        deadline = time.perf_counter() + timeout
        while True:
            remaining = deadline - time.perf_counter()
            if remaining <= 0:
                return first_event, timeout, events, "timeout"
            line = await asyncio.wait_for(reader.readline(), timeout=remaining)
            if not line:
                break
            text = line.decode(errors="replace").strip()
            if text.startswith("event:"):
                events += 1
                if first_event is None:
                    first_event = time.perf_counter() - started
                if text == "event:done":
                    saw_done = True
                    break
                if text == "event:error":
                    return first_event, time.perf_counter() - started, events, "refused"
        # A stream that ends WITHOUT event:done is a rejection, not a completed interview. The
        # first version of this harness counted those as successes and reported 114 concurrent
        # sessions - while the server's own metrics showed only 169 of 272 attempts finishing. The
        # agentExecutor rejects beyond 16 threads + 32 queue, which is the designed behaviour; a
        # load test that scores rejections as successes measures nothing.
        if not saw_done:
            return first_event, time.perf_counter() - started, events, "no_done"
        return first_event, time.perf_counter() - started, events, None

    except asyncio.TimeoutError:
        return first_event, time.perf_counter() - started, events, "timeout"
    except Exception as e:                                   # noqa: BLE001
        return first_event, time.perf_counter() - started, events, type(e).__name__
    finally:
        if writer is not None:
            writer.close()


async def run_level(base_url, candidate_ids, concurrency, timeout):
    """One point on the curve: `concurrency` sessions started at once."""
    tasks = [one_session(base_url, candidate_ids[i % len(candidate_ids)], timeout)
             for i in range(concurrency)]
    started = time.perf_counter()
    results = await asyncio.gather(*tasks)
    wall = time.perf_counter() - started

    ok = [r for r in results if r[3] is None]          # completed: reached event:done
    firsts = [r[0] for r in ok if r[0] is not None]
    totals = [r[1] for r in ok]
    errors = {}
    for r in results:
        if r[3]:
            errors[r[3]] = errors.get(r[3], 0) + 1

    def pct(values, p):
        if not values:
            return None
        ordered = sorted(values)
        return ordered[min(len(ordered) - 1, int(len(ordered) * p))]

    return {
        "concurrency": concurrency,
        "completed": len(ok),
        "failed": len(results) - len(ok),
        "errors": errors,
        "wallSeconds": round(wall, 2),
        "firstEventP50": round(pct(firsts, 0.50), 3) if firsts else None,
        "firstEventP95": round(pct(firsts, 0.95), 3) if firsts else None,
        "totalP50": round(pct(totals, 0.50), 2) if totals else None,
        "totalP95": round(pct(totals, 0.95), 2) if totals else None,
        "throughput": round(len(ok) / wall, 2) if wall else None,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--levels", default="1,2,5,10,20,40,80")
    parser.add_argument("--timeout", type=float, default=120)
    parser.add_argument("--candidates", default=None,
                        help="comma-separated candidate ids; defaults to the demo pair")
    parser.add_argument("--out", default=str(HERE / "loadtest_results.json"))
    args = parser.parse_args()

    candidate_ids = (args.candidates.split(",") if args.candidates
                     else ["2087819626484678658", "2087819626509844482"])
    levels = [int(x) for x in args.levels.split(",")]

    print("=" * 78)
    print("LOAD TEST - app tier, MODEL STUBBED, PER NODE")
    print("Not end-to-end capacity: one 7B model on one GPU serialises, so a real-model curve")
    print("would flatten at 1-2 sessions and describe Ollama rather than this application.")
    print("=" * 78)
    print(f"\n  target {args.base_url}   candidates {len(candidate_ids)}\n")
    print(f"  {'conc':>5} {'ok':>5} {'fail':>5} {'first p50':>10} {'first p95':>10} "
          f"{'total p50':>10} {'sess/s':>8}  errors")

    rows = []
    for level in levels:
        row = asyncio.run(run_level(args.base_url, candidate_ids, level, args.timeout))
        rows.append(row)
        errs = ",".join(f"{k}:{v}" for k, v in row["errors"].items()) or "-"
        print(f"  {row['concurrency']:>5} {row['completed']:>5} {row['failed']:>5} "
              f"{str(row['firstEventP50']):>10} {str(row['firstEventP95']):>10} "
              f"{str(row['totalP50']):>10} {str(row['throughput']):>8}  {errs}")

        # A level where most sessions fail says the knee is behind us; going further just produces
        # noise and a long wait.
        if row["failed"] > row["completed"]:
            print(f"\n  >> majority failed at {level} concurrent - the knee is below this. Stopping.")
            break

    Path(args.out).write_text(json.dumps({
        "note": "App tier only, model stubbed via the loadtest profile. PER NODE: EmitterRegistry "
                "is an in-memory map because RedisOperator has no pub/sub.",
        "levels": rows}, indent=2))
    print(f"\n  written to {args.out}")

    usable = [r for r in rows if r["failed"] == 0]
    if usable:
        best = max(usable, key=lambda r: r["concurrency"])
        print(f"\n  Highest level with zero failures: {best['concurrency']} concurrent sessions")
        print(f"  Report it as: \"{best['concurrency']} concurrent SSE interview sessions per node "
              f"(app tier, model stubbed)\"")
    return 0


if __name__ == "__main__":
    sys.exit(main())
