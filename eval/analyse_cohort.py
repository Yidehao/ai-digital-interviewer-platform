#!/usr/bin/env python3
"""
Analyse the simulated cohort: does the grader rank by competence, and does phrasing move the score
independently of competence?

THE DESIGN

  3 quality tiers x 4 surface profiles = 12 participants. Quality genuinely differs between tiers.
  Surface differs within a tier while the technical facts stay identical. That factorial structure
  is what lets the two effects be separated - C4 could only show a delta on one participant, which
  could always have been that particular answer.

WHAT THIS ESTABLISHES

  Construct validity. Spearman between the designed rank (weak < mixed < strong) and the grader's
  score. A grader that cannot order these is not measuring competence, and agreement with a human
  would not rescue it - it would just mean the human agreed with something meaningless.

  Surface effect controlled for quality. The same phrasing shift measured at three competence
  levels. A delta that appears at all three is a property of the grader; one that appears at one
  is noise.

WHAT THIS IS NOT

  Not agreement with humans. There are no human labels here. Generating them would make
  "grader vs human" mean "grader vs the script that wrote both sides" - a number that looks like
  evidence and is not.

  Not a human ceiling. That needs two people rating the same transcripts.

  eval/labeling_sheet.csv is written for exactly that: hand it to a person and the empty rows fill
  in. One labeler makes a single-labeler golden set, which is a real thing that can be reported as
  long as it is called that. Two makes a ceiling.

    python3 eval/analyse_cohort.py
"""

import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from metrics import spearman, fmt  # noqa: E402

HERE = Path(__file__).parent
TIER_ORDER = ["weak", "mixed", "strong"]


def load():
    path = HERE / "cohort_results.json"
    if not path.exists():
        print("no cohort_results.json — generate it first:")
        print("  cd backend && mvn -pl api spring-boot:run "
              "-Dspring-boot.run.profiles=dev,cohort")
        return None
    return json.loads(path.read_text())["participants"]


def report_ranking(rows):
    """Construct validity: does the grader order the tiers as designed?"""
    designed = [r["designedRank"] for r in rows]
    scored = [r["overall"] for r in rows]
    rho = spearman(designed, scored)

    print("Construct validity — does the grader rank by designed competence?")
    print(f"  Spearman(designed rank, grader overall)   {fmt(rho)}   n={len(rows)}")

    by_tier = defaultdict(list)
    for r in rows:
        by_tier[r["designedTier"]].append(r["overall"])
    print("\n  mean grader score by designed tier:")
    for tier in TIER_ORDER:
        scores = by_tier.get(tier, [])
        if scores:
            mean = sum(scores) / len(scores)
            print(f"    {tier:<8} {mean:.2f}   {sorted(scores)}")

    strong = by_tier.get("strong", [])
    weak = by_tier.get("weak", [])
    if strong and weak:
        gap = sum(strong) / len(strong) - sum(weak) / len(weak)
        print(f"\n  strong - weak gap: {gap:+.2f} points")
        if gap <= 0:
            print("  >> The grader does not separate designed-strong from designed-weak.")
            print("     Nothing else in this file matters until that is fixed.")
        elif gap < 1.0:
            print("  >> Under one point of separation across the full designed range.")
            print("     A rubric that compresses this hard cannot support a hiring decision.")


def report_surface(rows):
    """Surface effect, controlled for quality: same shift, three competence levels."""
    by_cell = {(r["designedTier"], r["surface"]): r for r in rows}
    surfaces = sorted({r["surface"] for r in rows} - {"neutral"})

    print("\nSurface effect, controlled for quality")
    print("  (delta vs the `neutral` phrasing of the SAME tier — facts identical)")
    print(f"  {'surface':<12} " + "  ".join(f"{t:>8}" for t in TIER_ORDER) + "     mean")

    for surface in surfaces:
        deltas = []
        cells = []
        for tier in TIER_ORDER:
            base = by_cell.get((tier, "neutral"))
            other = by_cell.get((tier, surface))
            if base and other:
                d = other["overall"] - base["overall"]
                deltas.append(d)
                cells.append(f"{d:+d}" if d else " 0")
            else:
                cells.append("  -")
        mean = sum(deltas) / len(deltas) if deltas else 0
        flag = ""
        if deltas and all(d < 0 for d in deltas):
            flag = "   <- penalised at EVERY competence level"
        elif deltas and all(d > 0 for d in deltas):
            flag = "   <- rewarded at EVERY competence level"
        print(f"  {surface:<12} " + "  ".join(f"{c:>8}" for c in cells)
              + f"   {mean:+.2f}{flag}")

    print("\n  A delta consistent across all three tiers is a property of the grader.")
    print("  One that appears at a single tier is noise — see the C4 reproducibility caveat.")


def report_range(rows):
    scores = [r["overall"] for r in rows]
    dims = defaultdict(list)
    for r in rows:
        for name, score in r["dimensions"].items():
            dims[name].append(score)

    print("\nRange use")
    print(f"  overall spans {min(scores)}-{max(scores)} of the 1-5 scale "
          f"({len(set(scores))} distinct values across {len(scores)} participants)")
    for name, values in dims.items():
        print(f"    {name:<22} {min(values)}-{max(values)}  mean {sum(values)/len(values):.2f}")
    if max(scores) - min(scores) <= 1:
        print("  >> The grader is using one point of a five point scale.")


def write_labeling_sheet(rows):
    """
    The cheapest possible route to a real ceiling.

    Every transcript here, with an empty score column. One person filling it in produces a
    single-labeler golden set — reportable, as long as it is called that. Two people produce a
    ceiling, which is what makes every other agreement number interpretable.
    """
    path = HERE / "labeling_sheet.csv"

    # NEVER overwrite labels that exist. This function opened the file "w" unconditionally, so
    # every run of the cohort analysis silently destroyed the human labels - the single most
    # expensive artefact in the project, two people's judgement on twelve transcripts, wiped by a
    # read-only-sounding script called "analyse". It happened, and the labels survived only because
    # eval/labeling/sheet-*.csv is the source of truth and --unblind can rebuild this file.
    if path.exists():
        with path.open() as existing:
            filled = [line for line in existing.readlines()[1:] if line.split(",")[1:2] != [""]]
        if filled:
            print(f"\n{path.name} already holds {len(filled)} labeled rows - left untouched.")
            print("  Delete it deliberately if you want a fresh blank sheet, or use")
            print("  eval/make_labeling_packet.py, which writes a blinded packet instead.")
            return

    with path.open("w", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["participant_id", "labeler", "overall_1_5", "correctness", "depth",
                         "communication", "practical_experience", "notes"])
        for r in rows:
            writer.writerow([r["id"], "", "", "", "", "", "", ""])
    print(f"\nLabeling sheet written to {path}")
    print("  Fill the `labeler` column with your name and score each row without looking at the")
    print("  model's verdicts. One labeler gives a single-labeler golden set; a second gives the")
    print("  human ceiling, which is the number that makes every other number mean something.")


def main():
    rows = load()
    if not rows:
        return 1

    print("=" * 78)
    print("SIMULATED COHORT — designed quality, authored surface. No human labels.")
    print("Establishes ranking behaviour and surface sensitivity. NOT agreement, NOT a ceiling.")
    print("=" * 78 + "\n")

    report_ranking(rows)
    report_surface(rows)
    report_range(rows)
    write_labeling_sheet(rows)
    return 0


if __name__ == "__main__":
    sys.exit(main())
