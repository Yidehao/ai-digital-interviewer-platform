#!/usr/bin/env python3
"""
Phase 0.5 / A1 - tool-call emission benchmark.

Answers one blocking question: does a local model reliably emit tool calls at all?

The whole agent design - six typed tools, schema validation, the fallback ladder - assumes
it does. If emission is low, every turn lands on fallback rung 1 and what gets built is an
elaborate scripted interviewer wearing an agent's clothes. Better to find out in half a day
than after three phases.

Two numbers per model:
  emission rate  - fraction of situations producing a tool call rather than prose
  validity rate  - fraction of emitted calls whose arguments pass their JSON Schema

Both matter and they fail differently. Low emission means the model does not understand it
is supposed to act. High emission with low validity means it understands but cannot fill in
the arguments, which the repair rung can sometimes save.

Usage:
    python3 eval/bench_tools.py                          # default models
    python3 eval/bench_tools.py --models qwen2.5:7b-instruct llama3.2:3b
    python3 eval/bench_tools.py --repeat 3               # 3 samples per situation
"""

import argparse
import json
import sys
import time
from pathlib import Path

import requests
from jsonschema import Draft202012Validator

sys.path.insert(0, str(Path(__file__).parent))
from situations import SITUATIONS                      # noqa: E402
from tools.schemas import VERSIONS, SYSTEM_PROMPT  # noqa: E402

# rebound by main() when --schema-version is given
TOOLS, ARG_SCHEMAS = VERSIONS["v1"]

OLLAMA = "http://localhost:11434"
TIMEOUT = 180

VALIDATORS = {name: Draft202012Validator(schema) for name, schema in ARG_SCHEMAS.items()}


def normalise_args(tool, args, coerce=True):
    """
    Two leniencies applied before validation, both of which the real ToolSchemas should copy.

    1. Drop explicit nulls for optional fields. Models routinely pass `"topic": null` to mean
       "not provided"; treating that as a type error burns a repair turn for nothing. Nulls on
       *required* fields are left alone - those are genuine errors worth reporting back.

    2. Coerce numeric strings to numbers when the schema demands a number. Small models emit
       `"timeoutMs": "3000"` and `"score": "2"` constantly. This is a JSON-encoding artifact,
       not a reasoning failure, and rejecting it wastes the repair budget on nothing.

    Returns (args, coerced_fields) so the benchmark can report how much of the validity rate
    the coercion is responsible for.
    """
    schema = ARG_SCHEMAS.get(tool, {})
    required = set(schema.get("required", []))
    props = schema.get("properties", {})

    out, coerced = {}, []
    for k, v in args.items():
        if v is None and k not in required:
            continue
        want = props.get(k, {}).get("type")
        if coerce and isinstance(v, str) and want in ("integer", "number"):
            try:
                out[k] = int(v) if want == "integer" else float(v)
                coerced.append(k)
                continue
            except ValueError:
                pass
        if coerce and isinstance(v, str) and want == "boolean" and v.lower() in ("true", "false"):
            out[k] = v.lower() == "true"
            coerced.append(k)
            continue
        out[k] = v
    return out, coerced


def extract_content_call(content):
    """
    Recover a tool call the model serialised into the message body instead of tool_calls.

    Small models do this regularly, and it is worth counting separately: prose that happens
    to be a well-formed call is a different failure from prose that is genuinely an answer.
    The first is recoverable with a parser; the second is fallback rung 1.
    """
    if not content:
        return None
    text = content.strip()
    start, end = text.find("{"), text.rfind("}")
    if start == -1 or end <= start:
        return None
    try:
        obj = json.loads(text[start:end + 1])
    except json.JSONDecodeError:
        return None
    if isinstance(obj, dict) and "name" in obj and obj["name"] in ARG_SCHEMAS:
        return obj["name"], obj.get("arguments") or obj.get("parameters") or {}
    return None


def call_model(model, messages, temperature=0.4):
    """One non-streaming /api/chat call with the six tools attached."""
    body = {
        "model": model,
        "messages": [{"role": "system", "content": SYSTEM_PROMPT}] + messages,
        "tools": TOOLS,
        "stream": False,
        "options": {"temperature": temperature},
    }
    t0 = time.time()
    r = requests.post(f"{OLLAMA}/api/chat", json=body, timeout=TIMEOUT)
    r.raise_for_status()
    return r.json(), time.time() - t0


def evaluate(situation, model, temperature):
    """Run one situation and classify the outcome."""
    try:
        resp, elapsed = call_model(model, situation["messages"], temperature)
    except Exception as e:                                    # noqa: BLE001
        return dict(id=situation["id"], outcome="request_failed", tool=None,
                    errors=[str(e)[:200]], elapsed=0.0, expected_hit=None)

    msg = resp.get("message", {}) or {}
    calls = msg.get("tool_calls") or []
    channel = "tool_calls"

    if not calls:
        recovered = extract_content_call(msg.get("content"))
        if recovered is None:
            # Genuine fallback rung 1: prose where a tool call was required.
            return dict(id=situation["id"], outcome="no_tool_call", tool=None,
                        errors=[], elapsed=elapsed, expected_hit=False, channel="content",
                        prose=(msg.get("content") or "")[:160])
        name, args = recovered
        channel = "content"
    else:
        fn = (calls[0].get("function") or {})
        name = fn.get("name")
        args = fn.get("arguments")

    if isinstance(args, str):                                 # some builds return a JSON string
        try:
            args = json.loads(args)
        except json.JSONDecodeError:
            return dict(id=situation["id"], outcome="unparseable_args", tool=name,
                        errors=["arguments were not valid JSON"], elapsed=elapsed,
                        channel=channel, expected_hit=name in situation["expected"])
    if args is None:
        args = {}

    if name not in VALIDATORS:
        return dict(id=situation["id"], outcome="unknown_tool", tool=name,
                    errors=[f"no such tool: {name}"], elapsed=elapsed,
                    channel=channel, expected_hit=False)

    raw_args, _ = normalise_args(name, args, coerce=False)
    lenient_args, coerced = normalise_args(name, args, coerce=True)

    raw_errors = [f"{'/'.join(str(p) for p in e.path) or '(root)'}: {e.message}"
                  for e in VALIDATORS[name].iter_errors(raw_args)]
    errors = [f"{'/'.join(str(p) for p in e.path) or '(root)'}: {e.message}"
              for e in VALIDATORS[name].iter_errors(lenient_args)]

    return dict(id=situation["id"],
                outcome="valid" if not errors else "invalid_args",
                valid_without_coercion=not raw_errors,
                coerced=coerced, args=lenient_args,
                tool=name, errors=errors[:3], elapsed=elapsed, channel=channel,
                expected_hit=name in situation["expected"])


def repair_once(model, situation, tool, bad_args, errors, temperature):
    """
    Fallback rung 2, measured rather than assumed.

    The plan's response to schema-invalid arguments is to hand the validation messages back
    verbatim and allow exactly one retry. Whether that actually works is the difference
    between a 60% usable model and a 90% usable one, so it is worth measuring before three
    phases are built on the assumption.
    """
    messages = list(situation["messages"]) + [
        {"role": "assistant", "content": "",
         "tool_calls": [{"function": {"name": tool, "arguments": bad_args}}]},
        {"role": "tool", "content": json.dumps({
            "error": "invalid_arguments",
            "tool": tool,
            "validation_errors": errors,
            "hint": "Call the tool again with corrected arguments.",
        })},
    ]
    try:
        resp, _ = call_model(model, messages, temperature)
    except Exception:                                             # noqa: BLE001
        return False
    calls = (resp.get("message") or {}).get("tool_calls") or []
    if not calls:
        return False
    fn = calls[0].get("function") or {}
    name, args = fn.get("name"), fn.get("arguments") or {}
    if isinstance(args, str):
        try:
            args = json.loads(args)
        except json.JSONDecodeError:
            return False
    if name not in VALIDATORS:
        return False
    args, _ = normalise_args(name, args)
    return not list(VALIDATORS[name].iter_errors(args))


def run_model(model, repeat, temperature, repair=False):
    rows = []
    for i, sit in enumerate(SITUATIONS, 1):
        for rep in range(repeat):
            row = evaluate(sit, model, temperature)
            row["rep"] = rep
            if repair and row["outcome"] == "invalid_args":
                row["repaired"] = repair_once(model, sit, row["tool"], row.get("args", {}),
                                              row["errors"], temperature)
            rows.append(row)
            mark = {"valid": ".", "invalid_args": "x", "no_tool_call": "P",
                    "unknown_tool": "?", "unparseable_args": "j",
                    "request_failed": "!"}.get(row["outcome"], "-")
            print(mark, end="", flush=True)
        if i % 10 == 0:
            print(f" {i}/{len(SITUATIONS)}", flush=True)
    print()
    return rows


def summarise(rows):
    n = len(rows)
    emitted = [r for r in rows if r["outcome"] not in ("no_tool_call", "request_failed")]
    valid = [r for r in rows if r["outcome"] == "valid"]
    hits = [r for r in rows if r.get("expected_hit")]
    lat = [r["elapsed"] for r in rows if r["elapsed"] > 0]
    native = [r for r in emitted if r.get("channel") == "tool_calls"]
    strict = [r for r in emitted if r.get("valid_without_coercion")]
    repaired = [r for r in rows if r.get("repaired")]
    attempted = [r for r in rows if "repaired" in r]
    return dict(
        n=n,
        emission_rate=len(emitted) / n if n else 0.0,
        native_emission_rate=len(native) / n if n else 0.0,
        validity_rate=len(valid) / len(emitted) if emitted else 0.0,
        validity_rate_strict=len(strict) / len(emitted) if emitted else 0.0,
        end_to_end_rate=len(valid) / n if n else 0.0,
        plausible_choice_rate=len(hits) / n if n else 0.0,
        repair_success_rate=len(repaired) / len(attempted) if attempted else None,
        valid_after_repair_rate=(len(valid) + len(repaired)) / n if n else 0.0,
        median_latency=sorted(lat)[len(lat) // 2] if lat else 0.0,
    )


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--models", nargs="+",
                    default=["qwen2.5:7b-instruct", "llama3.2:3b"])
    ap.add_argument("--repeat", type=int, default=1)
    ap.add_argument("--temperature", type=float, default=0.4)
    ap.add_argument("--schema-version", choices=list(VERSIONS), default="v1",
                    help="v1 as first measured; v2 applies the fixes in tool-design-findings.md")
    ap.add_argument("--repair", action="store_true",
                    help="measure fallback rung 2: one retry with the validation errors")
    ap.add_argument("--out", default=str(Path(__file__).parent / "bench_tools_raw.json"))
    args = ap.parse_args()

    global TOOLS, ARG_SCHEMAS, VALIDATORS
    TOOLS, ARG_SCHEMAS = VERSIONS[args.schema_version]
    VALIDATORS = {n: Draft202012Validator(sc) for n, sc in ARG_SCHEMAS.items()}
    print(f"schema version: {args.schema_version}")

    results = {}
    for model in args.models:
        print(f"\n=== {model} ===  (. valid  x invalid args  P prose/no call  ? unknown  ! error)")
        rows = run_model(model, args.repeat, args.temperature, args.repair)
        results[model] = dict(summary=summarise(rows), rows=rows)
        s = results[model]["summary"]
        print(f"  emission {s['emission_rate']:.0%}   "
              f"valid-args {s['validity_rate']:.0%} (strict {s['validity_rate_strict']:.0%})   "
              f"end-to-end {s['end_to_end_rate']:.0%}   "
              f"plausible-choice {s['plausible_choice_rate']:.0%}   "
              f"median {s['median_latency']:.1f}s")
        if s.get("repair_success_rate") is not None:
            print(f"  after 1 repair turn: {s['valid_after_repair_rate']:.0%} valid "
                  f"(rung 2 fixed {s['repair_success_rate']:.0%} of invalid calls)")

    Path(args.out).write_text(json.dumps(results, indent=2))
    print(f"\nraw results -> {args.out}")


if __name__ == "__main__":
    main()
