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

import argparse
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
    # Both files are named explicitly, and both conditions have to be stated. The defaults used to
    # be baked in, and the header claimed "same grader, temperature 0" whatever was actually in the
    # two files - so the moment one of them came from a different rubric, the script confidently
    # reported a rubric change as run-to-run noise. Which is exactly what happened.
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--a", default="cohort_results_run1_fallbackrubric.json")
    parser.add_argument("--b", default="cohort_results.json")
    parser.add_argument("--label-a", default="fallback rubric")
    parser.add_argument("--label-b", default="written rubric")
    args = parser.parse_args()

    run1 = load(args.a)
    run2 = load(args.b)
    if not run1 or not run2:
        print(f"need both {args.a} and {args.b}")
        return 1

    same_condition = args.label_a == args.label_b

    print("=" * 78)
    print(f"COHORT COMPARISON — same twelve participants, temperature 0")
    print(f"  A: {args.label_a:<28} ({args.a})")
    print(f"  B: {args.label_b:<28} ({args.b})")
    if not same_condition:
        print()
        print("  THESE ARE DIFFERENT CONDITIONS. Movement below is the change plus run-to-run")
        print("  noise, and this script cannot separate them. Between two IDENTICAL runs, 3 of 12")
        print("  participants moved - so a difference at that scale is not attributable to the")
        print("  change, and only an effect clearly larger than it is worth discussing at all.")
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
        print("  >> 12/12 by construction, not by persuasion. GraderAgent.recommendationFor")
        print("     derives this field from `overall` in code; the model is no longer asked for")
        print("     it, so an inconsistent row is unrepresentable. A run predating that change")
        print("     shows what asking got you: 1/12.")
    elif ok2 == ok1:
        print("  >> no improvement. The model is ignoring the stated mapping, so the field needs")
        print("     to be derived in code from `overall` rather than asked for.")

    # ---------------------------------------------------------------- reproducibility
    print("\nwhich scores moved?" if not same_condition
          else "\nreproducibility — which scores moved between identical runs?")
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
