#!/usr/bin/env python3
"""
Compare two cohort runs.

Two jobs, and the second is the more important one.

  1. Did the `recommendation` fix work? The enum was defined without saying how it relates to
     `overall`, so the model had no reason to keep them consistent, and eleven of twelve verdicts
     came back strong_yes including candidates scored 2 of 5. The schema now states the mapping.

  2. WHICH FINDINGS REPRODUCE. This matters more. The C4 harness produced a delta that did not
     survive a second run at temperature 0, which means any single-run number from this pipeline is
     provisional until it repeats. Running the same twelve participants twice turns "the grader
     penalises X" into either a finding or a coincidence, and there is no way to tell them apart
     from one run.

Anything that moves between runs cannot be reported. Anything that holds can.

    python3 eval/compare_runs.py
"""

import json
import sys
from pathlib import Path

HERE = Path(__file__).parent
DIMS = ["correctness", "depth", "communication", "practical_experience"]
EXPECTED = {1: "strong_no", 2: "no", 3: "borderline", 4: "yes", 5: "strong_yes"}


def load(name):
    path = HERE / name
    if not path.exists():
        return None
    return {r["id"]: r for r in json.loads(path.read_text())["participants"]}


def main():
    run1 = load("cohort_results_run1.json")
    run2 = load("cohort_results.json")
    if not run1 or not run2:
        print("need both cohort_results_run1.json and cohort_results.json")
        return 1

    print("=" * 78)
    print("RUN 1 vs RUN 2 — same twelve participants, same grader, temperature 0")
    print("=" * 78)

    # ---------------------------------------------------------------- the fix
    print("\nrecommendation consistency (does it follow from `overall`?)")
    print(f"  {'participant':<20} {'run 1':>26}   {'run 2':>26}")
    ok1 = ok2 = 0
    for pid in run1:
        if pid not in run2:
            continue
        a, b = run1[pid], run2[pid]
        want1, want2 = EXPECTED.get(a["overall"]), EXPECTED.get(b["overall"])
        a_ok = a["recommendation"] == want1
        b_ok = b["recommendation"] == want2
        ok1 += a_ok
        ok2 += b_ok
        left = "{} -> {}".format(a["overall"], a["recommendation"])
        right = "{} -> {}".format(b["overall"], b["recommendation"])
        print("  {:<20} {:>20} {:>5}   {:>20} {:>5}".format(
            pid, left, "ok" if a_ok else "BAD", right, "ok" if b_ok else "BAD"))
    total = len(run1)
    print(f"\n  consistent: run 1 {ok1}/{total}   run 2 {ok2}/{total}")
    if ok2 > ok1:
        print("  >> the schema fix worked: stating the mapping was enough.")
    elif ok2 == ok1:
        print("  >> no improvement. The model is ignoring the stated mapping, so the field needs")
        print("     to be derived in code from `overall` rather than asked for.")

    # ---------------------------------------------------------------- reproducibility
    print("\nreproducibility — which scores moved between identical runs?")
    moved = stable = 0
    for pid in sorted(run1):
        if pid not in run2:
            continue
        a, b = run1[pid], run2[pid]
        deltas = {d: b["dimensions"][d] - a["dimensions"][d] for d in DIMS}
        overall_delta = b["overall"] - a["overall"]
        if overall_delta or any(deltas.values()):
            moved += 1
            parts = [f"overall {overall_delta:+d}"] if overall_delta else []
            parts += [f"{d} {v:+d}" for d, v in deltas.items() if v]
            print(f"  {pid:<20} {', '.join(parts)}")
        else:
            stable += 1
    print(f"\n  identical: {stable}/{stable + moved}    moved: {moved}/{stable + moved}")

    # ---------------------------------------------------------------- findings that survive
    print("\nfindings that hold in BOTH runs")
    for label, surface in [("non-native phrasing", "non_native"),
                           ("verbosity", "verbose"),
                           ("terseness", "terse")]:
        held = []
        for tier in ["strong", "mixed", "weak"]:
            base_id, var_id = f"{tier}-neutral", f"{tier}-{surface}"
            if not all(k in run1 and k in run2 for k in (base_id, var_id)):
                continue
            d1 = {d: run1[var_id]["dimensions"][d] - run1[base_id]["dimensions"][d] for d in DIMS}
            d2 = {d: run2[var_id]["dimensions"][d] - run2[base_id]["dimensions"][d] for d in DIMS}
            agree = {d: d1[d] for d in DIMS if d1[d] == d2[d] and d1[d] != 0}
            if agree:
                held.append((tier, agree))
        if held:
            print(f"  {label}:")
            for tier, agree in held:
                detail = ", ".join(f"{d} {v:+d}" for d, v in agree.items())
                print(f"    {tier:<8} {detail}")
        else:
            print(f"  {label}: nothing reproduced across both runs")

    print("\n  Only the lines above are reportable. Everything else was one run's noise.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
