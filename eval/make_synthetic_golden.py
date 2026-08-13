#!/usr/bin/env python3
"""
Generate a SYNTHETIC golden set: fake participants, fake transcripts, fake human labels.

=============================================================================================
WHAT THIS IS AND IS NOT
=============================================================================================
This is a **pipeline fixture**, not a benchmark. It exists so `run_eval.py`, the metrics, the
report format and the file schema can be built and tested without waiting on ten people's
calendars. Everything it produces is machine-generated and is marked `"synthetic": true` in
every file it writes.

It CAN legitimately be used to:
  - develop and debug the eval harness end to end
  - verify the QWK / MAE / Spearman implementations against known-agreement label sets
  - exercise the grader on realistic input and shake out crashes
  - settle the file format before real data exists

It CANNOT be used to:
  - report grading accuracy
  - report a human ceiling — the "two labelers" here are one random number generator with two
    seeds, so their agreement is a property of this script, not of human judgement
  - support any claim containing the words "human-labeled"

The distinction matters because the résumé claim is specifically about benchmarking against
human labels. Numbers from this file are not that, and anything derived from them must say so.
Real sessions replace these files one for one; the format is identical, and `synthetic` flips
to false.

=============================================================================================

Labels are generated from a latent ability per participant per dimension, then observed twice
with independent noise and a small systematic bias on one labeler — which is roughly how two
humans actually differ. That makes the fixture useful for checking that the agreement metrics
respond correctly to a *known* level of disagreement, which is the one thing a synthetic set is
genuinely good for.

Usage:
    python3 eval/make_synthetic_golden.py                  # 10 participants x 2 sessions
    python3 eval/make_synthetic_golden.py --participants 6 --seed 7
"""

import argparse
import json
import random
from pathlib import Path

HERE = Path(__file__).parent
import sys
sys.path.insert(0, str(HERE))
from answers import ANSWERS, ASR_SUBSTITUTIONS, DISFLUENCIES  # noqa: E402

DIMENSIONS = ["correctness", "depth", "communication", "practical_experience"]
RECOMMENDATIONS = ["strong_no", "no", "lean_yes", "yes", "strong_yes"]


def load_bank():
    bank = json.loads((HERE / "question-bank.json").read_text())
    return bank["questions"]


def clamp(v, lo=1, hi=5):
    return max(lo, min(hi, v))


def make_participants(n, rng):
    """
    Each participant gets a latent ability per dimension.

    Abilities are correlated but not identical — someone can communicate well while being
    technically shaky, which is exactly the case where two graders disagree, and therefore the
    case worth having in the fixture.
    """
    people = []
    for i in range(1, n + 1):
        base = rng.uniform(1.6, 4.6)
        people.append({
            "pseudonym": f"P{i:02d}",
            "latent": {d: clamp(round(base + rng.uniform(-0.9, 0.9), 2), 1.0, 5.0)
                       for d in DIMENSIONS},
        })
    return people


def tier_for(ability, rng):
    """Pick an answer tier from a latent ability, with enough noise to be non-deterministic."""
    roll = ability + rng.uniform(-0.7, 0.7)
    if roll >= 3.8:
        return "strong"
    if roll >= 2.4:
        return "mixed"
    return "weak"


def add_speech_texture(text, rng):
    """Sprinkle disfluency and occasional ASR damage. Real transcripts are not clean prose."""
    if rng.random() < 0.55:
        parts = text.split(". ")
        if len(parts) > 1:
            idx = rng.randrange(1, len(parts))
            parts[idx] = rng.choice(DISFLUENCIES) + parts[idx][0].lower() + parts[idx][1:]
            text = ". ".join(parts)
    if rng.random() < 0.30:
        for src, dst in ASR_SUBSTITUTIONS:
            if src in text:
                text = text.replace(src, dst, 1)
                break
    return text


def build_session(person, questions, session_no, rng):
    turns, tiers = [], []
    seq = 0
    for q in questions:
        ability = person["latent"][q["competency"]]
        tier = tier_for(ability, rng)
        tiers.append((q["competency"], tier))

        turns.append({"seq": seq, "kind": "QUESTION", "questionId": q["key"],
                      "text": q["question"]})
        seq += 1
        answer = add_speech_texture(ANSWERS[q["key"]][tier], rng)
        turns.append({"seq": seq, "kind": "ANSWER", "questionId": q["key"], "text": answer,
                      "sttConfidence": round(rng.uniform(0.78, 0.97), 3)})
        seq += 1

    return {
        "synthetic": True,
        "sessionId": f"{person['pseudonym']}-s{session_no}",
        "participant": person["pseudonym"],
        "jobName": "SDE",
        "mode": "scripted",
        "totalSeconds": int(rng.uniform(560, 1180)),
        "transcript": turns,
        "_generatorTiers": tiers,
    }


def label(session, person, labeler, rng, bias=None):
    """
    One labeler's view of a session.

    A label is the latent ability observed through noise. The second labeler additionally
    carries a small systematic bias, because real second labelers do — they are harsher on some
    dimension, or they use the ends of the scale differently. Without that, agreement comes out
    unrealistically high and the metrics never get exercised on the interesting case.
    """
    bias = bias or {}
    dims = {}
    for d in DIMENSIONS:
        observed = person["latent"][d] + rng.gauss(0, 0.55) + bias.get(d, 0.0)
        dims[d] = int(clamp(round(observed)))
    overall = int(clamp(round(sum(dims.values()) / len(dims) + rng.gauss(0, 0.35))))
    return {
        "synthetic": True,
        "sessionId": session["sessionId"],
        "labeler": labeler,
        "overall": overall,
        "recommendation": RECOMMENDATIONS[overall - 1],
        "dimensions": dims,
        "notes": "",
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--participants", type=int, default=10)
    ap.add_argument("--sessions-per-participant", type=int, default=2)
    ap.add_argument("--overlap", type=int, default=10,
                    help="sessions that get a second labeler (the human-ceiling subset)")
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    rng = random.Random(args.seed)
    bank = load_bank()
    if len(bank) < args.sessions_per_participant * 5:
        raise SystemExit(f"question bank has {len(bank)} questions; "
                         f"need {args.sessions_per_participant * 5} for non-overlapping subsets")

    golden = HERE / "golden"
    labels_dir = HERE / "labels"
    for d in (golden, labels_dir):
        d.mkdir(exist_ok=True)
        for old in d.glob("*.json"):
            old.unlink()

    people = make_participants(args.participants, rng)
    # labeler B is slightly harsher on depth and slightly softer on communication
    b_bias = {"depth": -0.45, "communication": +0.30}

    sessions, all_labels = [], []
    for person in people:
        subsets = [bank[i * 5:(i + 1) * 5] for i in range(args.sessions_per_participant)]
        for n, qs in enumerate(subsets, start=1):
            s = build_session(person, qs, n, rng)
            sessions.append(s)
            all_labels.append(label(s, person, "A", rng))

    for s in sessions[:args.overlap]:
        person = next(p for p in people if p["pseudonym"] == s["participant"])
        all_labels.append(label(s, person, "B", rng, bias=b_bias))

    for s in sessions:
        (golden / f"session-{s['sessionId']}.json").write_text(json.dumps(s, indent=2))
    for lb in all_labels:
        (labels_dir / f"{lb['sessionId']}-{lb['labeler']}.json").write_text(json.dumps(lb, indent=2))

    (HERE / "recruiting" / "participants.csv").write_text(build_csv(people, args))

    print(f"SYNTHETIC fixture written — not a benchmark, see the module docstring")
    print(f"  {len(sessions)} sessions   -> eval/golden/")
    print(f"  {len(all_labels)} labels     -> eval/labels/  "
          f"({args.overlap} double-labeled for the ceiling calculation)")
    print(f"  {args.participants} participants -> eval/recruiting/participants.csv")


def build_csv(people, args):
    head = ("pseudonym,date,consent_given,consent_notes,session_ok,transcript_saved,"
            "audio_saved,anonymised,self_labeled,second_labeler_asked,second_labeler_agreed,"
            "session_notes\n")
    rows = []
    for i, p in enumerate(people):
        second = "y" if i == 0 else "n"
        rows.append(f"{p['pseudonym']},SYNTHETIC,n/a,SYNTHETIC FIXTURE - no real person,"
                    f"y,y,n,n/a,y,{second},{second},"
                    f"generated by make_synthetic_golden.py; replace with a real session\n")
    return head + "".join(rows)


if __name__ == "__main__":
    main()
