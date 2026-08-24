#!/usr/bin/env python3
"""
Build a BLINDED labeling packet: transcripts to read, and a sheet to score them on.

WHY BLINDED

  eval/labeling_sheet.csv identifies each row as `strong-non_native`, `weak-terse` and so on.
  That is the designed answer, printed next to the box you are about to score. A labeler who reads
  "strong" before reading the transcript is not producing an independent judgement, and the human
  ceiling computed from it would measure how well two people can read a filename.

  It is worse than a merely useless number, because the tier labels also name the SURFACE profile.
  The finding under test is whether phrasing moves scores independently of content - so a sheet
  that announces "this one is the non-native version" makes the one question the eval exists to
  answer unaskable.

  This script shuffles the twelve participants, gives them opaque ids, writes the transcripts in
  that order, and keeps the mapping in a separate key file you do not open until you are done.

WHAT IT WRITES

  eval/labeling/transcripts.md   what the labeler reads - twelve transcripts, no tier labels
  eval/labeling/sheet.csv        what the labeler fills in - opaque ids only
  eval/labeling/KEY.csv          blinded id -> participant id. DO NOT OPEN until scoring is done.

  A second labeler gets the SAME packet - same ids, same order - so the two sheets can be compared
  row by row. Pass --seed to regenerate an identical packet, or a new seed for a different order.

    python3 eval/make_labeling_packet.py
    python3 eval/make_labeling_packet.py --unblind    # after both sheets are filled in
"""

import argparse
import csv
import json
import random
import sys
from pathlib import Path

HERE = Path(__file__).parent
OUT = HERE / "labeling"
TIERS = ["strong", "mixed", "weak"]
DIMENSIONS = ["correctness", "depth", "communication", "practical_experience"]


def build(seed):
    fixture = json.loads((HERE / "simulated_cohort.json").read_text())
    surfaces = fixture["surface_profiles"]
    questions = fixture["questions"]

    participants = [f"{tier}-{surface}" for tier in TIERS for surface in surfaces]
    random.Random(seed).shuffle(participants)

    OUT.mkdir(exist_ok=True)
    labels = [chr(ord("A") + i) for i in range(len(participants))]

    # --- what the labeler reads ------------------------------------------------------------
    lines = [
        "# Transcripts to score",
        "",
        "Twelve candidates, same two questions. Score each one on the sheet before moving to the",
        "next, and **do not go back and adjust** - a first pass you then normalise is one judgement",
        "applied twelve times, not twelve judgements.",
        "",
        "You are not told anything about these candidates. That is deliberate.",
        "",
        "---",
        "",
    ]
    for label, pid in zip(labels, participants):
        tier, surface = pid.split("-", 1)
        lines.append(f"## Candidate {label}")
        lines.append("")
        for question in questions:
            lines.append(f"**Q: {question['question']}**")
            lines.append("")
            lines.append(question["tiers"][tier][surface])
            lines.append("")
        lines.append("---")
        lines.append("")
    (OUT / "transcripts.md").write_text("\n".join(lines))

    # --- what the labeler fills in ---------------------------------------------------------
    with (OUT / "sheet.csv").open("w", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["candidate", "labeler", "overall_1_5"] + DIMENSIONS + ["notes"])
        for label in labels:
            writer.writerow([label] + [""] * (2 + len(DIMENSIONS) + 1))

    # --- the mapping, kept apart -----------------------------------------------------------
    with (OUT / "KEY.csv").open("w", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["candidate", "participant_id", "designed_tier", "surface"])
        for label, pid in zip(labels, participants):
            tier, surface = pid.split("-", 1)
            writer.writerow([label, pid, tier, surface])

    print(f"packet written to {OUT}/")
    print(f"  transcripts.md   read this")
    print(f"  sheet.csv        fill this in  (copy it per labeler: sheet-yide.csv, sheet-alex.csv)")
    print(f"  KEY.csv          DO NOT OPEN until every row is scored")
    print(f"\n  seed {seed} - pass --seed {seed} to rebuild this exact packet for a second labeler")


def unblind():
    key = {row["candidate"]: row for row in csv.DictReader((OUT / "KEY.csv").open())}
    sheets = sorted(OUT.glob("sheet-*.csv"))
    if not sheets:
        print("no filled sheets found - name them sheet-<labeler>.csv in eval/labeling/")
        return 1

    rows = []
    for sheet in sheets:
        for row in csv.DictReader(sheet.open()):
            if not row.get("overall_1_5"):
                continue
            mapped = key.get(row["candidate"])
            if not mapped:
                continue
            rows.append({
                "participant_id": mapped["participant_id"],
                "labeler": row.get("labeler") or sheet.stem.replace("sheet-", ""),
                "overall_1_5": row["overall_1_5"],
                **{d: row.get(d, "") for d in DIMENSIONS},
                "notes": row.get("notes", ""),
            })

    if not rows:
        print("sheets found but no scores in them")
        return 1

    target = HERE / "labeling_sheet.csv"
    with target.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=[
            "participant_id", "labeler", "overall_1_5", *DIMENSIONS, "notes"])
        writer.writeheader()
        writer.writerows(rows)

    labelers = sorted({r["labeler"] for r in rows})
    print(f"unblinded {len(rows)} scored rows from {len(sheets)} sheet(s) -> {target}")
    print(f"  labelers: {', '.join(labelers)}")
    if len(labelers) < 2:
        print("  ONE labeler: this is a single-labeler golden set. Report it as exactly that.")
        print("  It is NOT a human ceiling - that needs a second person scoring the same twelve.")
    else:
        print("  Two or more labelers: run_eval.py can now compute a real human ceiling.")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--seed", type=int, default=20260824)
    parser.add_argument("--unblind", action="store_true")
    args = parser.parse_args()
    return unblind() if args.unblind else (build(args.seed) or 0)


if __name__ == "__main__":
    sys.exit(main())
