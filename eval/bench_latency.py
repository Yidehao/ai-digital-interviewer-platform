#!/usr/bin/env python3
"""
Phase 0.5 / A2 - prompt-eval throughput and first-token latency on this machine.

Every latency claim in the plan traces back to numbers this script produces. Measure before
writing any of them down; "p50 first token ~700 ms" was an assumption, not an observation.

Two things are measured, because they scale differently:

  prompt eval  - time to ingest the prompt. Grows with prompt length, and the six tool
                 schemas ride in every request, so this is the term that quietly degrades
                 as an interview accumulates context.
  first token  - wall-clock from request to the first streamed token. This is what a
                 candidate actually perceives, and it includes prompt eval.

Prompt sizes are chosen to bracket the real range: ~500 tokens is turn one, ~1500 is a
typical mid-interview turn with tool schemas and a few turns of history, ~3000 is a long
interview without a sliding window - which is what the plan's history-window is for.

Usage:
    python3 eval/bench_latency.py
    python3 eval/bench_latency.py --models qwen2.5:7b-instruct --sizes 1500
"""

import argparse
import json
import platform
import subprocess
import time
import uuid
from pathlib import Path

import requests

OLLAMA = "http://localhost:11434"

FILLER = (
    "The candidate described their approach to debugging a production incident in detail, "
    "covering dashboards, recent deploys, rollback strategy, and the postmortem process. "
    "The interviewer noted the emphasis on stopping customer impact before root causing. "
)


def make_prompt(target_tokens, nonce=None):
    """
    Roughly target_tokens of realistic interview text (~0.75 words per token).

    The nonce goes at the *front* on purpose. Ollama caches the KV state of a prompt prefix,
    so repeating an identical prompt makes runs 2..n report near-zero prompt-eval time - which
    shows up as throughput that rises with prompt length, an obvious impossibility. A unique
    leading token busts the prefix and forces a genuine cold evaluation every run.

    Measuring the cached path would be measuring nothing: in a real interview every turn has a
    new candidate answer appended, so the prompt is never byte-identical to the last one.
    """
    words_needed = int(target_tokens * 0.75)
    filler_words = FILLER.split()
    reps = words_needed // len(filler_words) + 1
    body = " ".join((filler_words * reps)[:words_needed])
    salt = nonce if nonce is not None else uuid.uuid4().hex
    return f"[session {salt}] {body}"


def measure(model, prompt, warmup=False):
    """One streamed /api/generate call, timing the first token and reading Ollama's counters."""
    body = {"model": model, "prompt": prompt, "stream": True,
            "options": {"temperature": 0, "num_predict": 40}}
    t0 = time.time()
    first_token_at = None
    final = {}
    with requests.post(f"{OLLAMA}/api/generate", json=body, stream=True, timeout=300) as r:
        r.raise_for_status()
        for line in r.iter_lines():
            if not line:
                continue
            chunk = json.loads(line)
            if first_token_at is None and chunk.get("response"):
                first_token_at = time.time()
            if chunk.get("done"):
                final = chunk
                break
    if warmup:
        return None

    ns = 1e9
    prompt_tokens = final.get("prompt_eval_count", 0)
    prompt_ns = final.get("prompt_eval_duration", 0)
    eval_tokens = final.get("eval_count", 0)
    eval_ns = final.get("eval_duration", 0)
    return dict(
        prompt_tokens=prompt_tokens,
        prompt_eval_s=prompt_ns / ns,
        prompt_tokens_per_s=prompt_tokens / (prompt_ns / ns) if prompt_ns else 0,
        eval_tokens=eval_tokens,
        gen_tokens_per_s=eval_tokens / (eval_ns / ns) if eval_ns else 0,
        first_token_s=(first_token_at - t0) if first_token_at else None,
        total_s=time.time() - t0,
    )


def hardware():
    def sh(cmd):
        try:
            return subprocess.run(cmd, shell=True, capture_output=True, text=True).stdout.strip()
        except Exception:                                        # noqa: BLE001
            return "unknown"
    return dict(
        cpu=sh("sysctl -n machdep.cpu.brand_string") or platform.processor(),
        ram_gb=round(int(sh("sysctl -n hw.memsize") or 0) / 1073741824) or None,
        platform=platform.platform(),
    )


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--models", nargs="+", default=["qwen2.5:7b-instruct", "llama3.2:3b"])
    ap.add_argument("--sizes", nargs="+", type=int, default=[500, 1500, 3000])
    ap.add_argument("--repeat", type=int, default=3)
    ap.add_argument("--out", default=str(Path(__file__).parent / "bench_latency_raw.json"))
    args = ap.parse_args()

    hw = hardware()
    print(f"hardware: {hw['cpu']}, {hw['ram_gb']} GB")
    results = {"hardware": hw, "models": {}}

    for model in args.models:
        print(f"\n=== {model} ===")
        measure(model, make_prompt(200), warmup=True)   # load weights, exclude from timings
        per_size = {}
        for size in args.sizes:
            # a fresh prompt per run - see make_prompt on why reuse measures the cache
            runs = [measure(model, make_prompt(size)) for _ in range(args.repeat)]
            med = lambda k: sorted(r[k] for r in runs if r[k] is not None)[len(runs) // 2]  # noqa: E731
            per_size[size] = dict(
                prompt_tokens=runs[0]["prompt_tokens"],
                prompt_tokens_per_s=med("prompt_tokens_per_s"),
                prompt_eval_s=med("prompt_eval_s"),
                first_token_s=med("first_token_s"),
                gen_tokens_per_s=med("gen_tokens_per_s"),
            )
            p = per_size[size]
            print(f"  ~{size:>4} tok target | {p['prompt_tokens']:>4} actual | "
                  f"prompt eval {p['prompt_eval_s']:.2f}s @ {p['prompt_tokens_per_s']:.0f} tok/s | "
                  f"first token {p['first_token_s']:.2f}s | gen {p['gen_tokens_per_s']:.1f} tok/s")
        results["models"][model] = per_size

    Path(args.out).write_text(json.dumps(results, indent=2))
    print(f"\nraw -> {args.out}")


if __name__ == "__main__":
    main()
