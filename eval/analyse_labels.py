#!/usr/bin/env python3
"""
The human ceiling, and the question the whole project has been waiting on.

WHAT THIS ANSWERS

  1. How well do two people agree with each other on this rubric? Without that number, every
     grader number is uninterpretable - QWK 0.60 is excellent against a ceiling of 0.62 and poor
     against 0.85.

  2. How well does the grader agree with them?

  3. THE ONE THAT DECIDES DEPLOYABILITY: the grader docks one point of CORRECTNESS from non-native
     phrasing at every competence tier, reproduced across two runs. Do the humans do that too?
     Human interviewers are known to carry this bias. If they show the same penalty, the grader is
     typical; if they show none, the grader has a defect humans do not.

     The cohort is built to make this checkable: within a tier the technical facts are IDENTICAL
     and only the phrasing differs, so any score gap between `neutral` and `non_native` is a
     response to phrasing alone.

    python3 eval/analyse_labels.py
"""

import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from metrics import compare, fmt  # noqa: E402

HERE = Path(__file__).parent
DIMENSIONS = ["correctness", "depth", "communication", "practical_experience"]
TIERS = ["strong", "mixed", "weak"]


def load_human():
    rows = list(csv.DictReader((HERE / "labeling_sheet.csv").open()))
    by_labeler = defaultdict(dict)
    for row in rows:
        if not row.get("overall_1_5"):
            continue
        scores = {"overall": int(row["overall_1_5"])}
        for d in DIMENSIONS:
            if row.get(d):
                scores[d] = int(row[d])
        by_labeler[row["labeler"]][row["participant_id"]] = scores
    return by_labeler


def load_grader():
    path = HERE / "cohort_results.json"
    if not path.exists():
        return {}
    out = {}
    for p in json.loads(path.read_text())["participants"]:
        scores = {"overall": p["overall"]}
        scores.update(p.get("dimensions", {}))
        out[p["id"]] = scores
    return out


def pair(a, b, field):
    ids = sorted(set(a) & set(b))
    return ([a[i][field] for i in ids if field in a[i] and field in b[i]],
            [b[i][field] for i in ids if field in a[i] and field in b[i]])


def surface_delta(scores, surface, field):
    """Score on `surface` minus score on `neutral`, within each tier. Facts are identical."""
    out = {}
    for tier in TIERS:
        neutral, variant = f"{tier}-neutral", f"{tier}-{surface}"
        if neutral in scores and variant in scores:
            if field in scores[neutral] and field in scores[variant]:
                out[tier] = scores[variant][field] - scores[neutral][field]
    return out


def main():
    humans = load_human()
    grader = load_grader()
    names = sorted(humans)

    print("=" * 78)
    print("HUMAN LABELS - real people, blinded to tier and surface profile")
    print("=" * 78)
    print(f"\n  labelers: {', '.join(names)}   participants scored: "
          f"{len(humans[names[0]]) if names else 0}")

    if len(names) < 2:
        print("\n  Only one labeler. This is a SINGLE-LABELER GOLDEN SET - report it as exactly")
        print("  that phrase. It is not a human ceiling and must never be described as one.")
        return 1

    a, b = names[0], names[1]

    print("\n" + "-" * 78)
    print(f"1. HUMAN CEILING - {a} vs {b}")
    print("-" * 78)
    print("   Every grader number below is read against this, not against 1.0.\n")
    x, y = pair(humans[a], humans[b], "overall")
    ceiling = compare(x, y)
    print(f"   overall        QWK {fmt(ceiling['qwk'])}   exact {fmt(ceiling['exact'])}   "
          f"within1 {fmt(ceiling['within_1'])}")
    for d in DIMENSIONS:
        x, y = pair(humans[a], humans[b], d)
        if x:
            c = compare(x, y)
            print(f"   {d:<14} QWK {fmt(c['qwk'])}   exact {fmt(c['exact'])}   "
                  f"within1 {fmt(c['within_1'])}")

    if not grader:
        print("\n   no cohort_results.json - grader comparison skipped")
        return 0

    print("\n" + "-" * 78)
    print("2. GRADER vs EACH HUMAN")
    print("-" * 78)
    for name in names:
        x, y = pair(grader, humans[name], "overall")
        c = compare(x, y)
        print(f"   vs {name:<10} QWK {fmt(c['qwk'])}   exact {fmt(c['exact'])}   "
              f"within1 {fmt(c['within_1'])}   bias {c['bias']:+.2f}")
    print(f"\n   Read against the ceiling of QWK {fmt(ceiling['qwk'])}.")

    print("\n" + "-" * 78)
    print("3. THE SURFACE PENALTY - do humans do what the grader does?")
    print("-" * 78)
    print("   Delta vs the `neutral` phrasing of the SAME tier. Facts identical; only")
    print("   phrasing differs, so any non-zero value is a response to phrasing alone.\n")

    for field in ["overall"] + DIMENSIONS:
        print(f"   {field}")
        for surface in ["non_native", "verbose", "terse"]:
            row = f"     {surface:<12}"
            for who, scores in [(a, humans[a]), (b, humans[b]), ("GRADER", grader)]:
                deltas = surface_delta(scores, surface, field)
                if deltas:
                    mean = sum(deltas.values()) / len(deltas)
                    detail = "/".join(f"{deltas.get(t, 0):+d}" for t in TIERS)
                    row += f"  {who}: {mean:+.2f} [{detail}]"
            print(row)
        print()

    print("   [strong/mixed/weak]. The grader's correctness row is the one that decides")
    print("   deployability: if the humans show the same penalty it is typical, and if they")
    print("   show none it is a defect humans do not have.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
