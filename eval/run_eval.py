#!/usr/bin/env python3
"""
Grader evaluation: how well does the model agree with humans, and is that good?

Reads verdicts written by GraderEvalRunner (Java) and labels from eval/labels/, and reports the
agreement metrics. The split is deliberate — the grader that produces verdicts here is literally
the production class, because a harness that reimplements the grader evaluates a system nobody
ships.

WHAT MAKES A NUMBER HERE MEANINGFUL

  Nothing in this file means anything without the human ceiling. A grader at QWK 0.60 is excellent
  if two humans only reach 0.62 with each other, and poor if they reach 0.85. The ceiling is the
  first thing printed for that reason, and every model number is reported as a fraction of it.

  QWK leads because it is the standard for ordinal rubric scoring: it corrects for chance agreement
  and penalises a 2-vs-5 far more than a 3-vs-4. Percent agreement does neither, which is why a
  grader that always says "3" looks strong on a dataset where most answers are average.

  Everything else is diagnostic. MAE catches systematic leniency. Spearman answers a different and
  sometimes more useful question — does it *rank* candidates the same way, even if shifted? A
  constant +1 bias has poor MAE and perfect Spearman, and for a recruiter sorting a shortlist that
  may be fine.

WHAT THIS CANNOT TELL YOU YET

  eval/labels/ is currently SYNTHETIC — machine-generated labels from a script with two seeds. The
  banner below fires when it sees them. Those labels verify that the metric code works; they say
  nothing about whether the grader agrees with a human, because no human was involved.

    python3 eval/run_eval.py
"""

import json
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from metrics import compare, fmt, load_labels, paired, DIMENSIONS  # noqa: E402

HERE = Path(__file__).parent


def load_verdicts(directory=None):
    """{sessionId: verdict record} as written by GraderEvalRunner."""
    directory = Path(directory or HERE / "verdicts")
    out = {}
    if not directory.exists():
        return out
    for path in sorted(directory.glob("verdict-*.json")):
        record = json.loads(path.read_text())
        out[record["sessionId"]] = record
    return out


def model_scores(verdicts):
    """{sessionId: {dimension: score, 'overall': score}}"""
    out = {}
    for session_id, record in verdicts.items():
        verdict = record["verdict"]
        scores = {d["name"]: d["score"] for d in verdict["dimensions"]}
        scores["overall"] = verdict["overall"]
        out[session_id] = scores
    return out


def human_ceiling(labels):
    """QWK between the two human labelers, on the sessions they both rated."""
    a, b = paired(labels, "A", "B")
    if not a:
        return None
    return compare(a, b)


def report_stability(verdicts):
    """B3 — does the verdict move when the prompt is reworded but not changed in meaning?"""
    deltas = [r.get("stability", {}).get("paraphraseOverallDelta")
              for r in verdicts.values()]
    deltas = [d for d in deltas if d is not None]
    if not deltas:
        print("\nStability: no paraphrase probes recorded")
        return

    moved = sum(1 for d in deltas if d != 0)
    print("\nStability — rubric paraphrase (B3)")
    print(f"  sessions probed          {len(deltas)}")
    print(f"  overall score moved      {moved}/{len(deltas)}")
    print(f"  largest movement         {max(abs(d) for d in deltas)}")
    print("  A score that moves when only the wording changes is responding to the prompt,")
    print("  not to the candidate. Self-consistency at temperature 0 measures neither.")


def report_disagreements(labels, scores, limit=10):
    """The N biggest human-vs-model gaps, which is where you learn what the grader misreads."""
    rows = []
    for session_id, by_rater in labels.items():
        if "A" not in by_rater or session_id not in scores:
            continue
        human = by_rater["A"]["overall"]
        model = scores[session_id]["overall"]
        rows.append((abs(human - model), session_id, human, model))
    if not rows:
        return
    rows.sort(reverse=True)
    print(f"\nLargest disagreements (top {min(limit, len(rows))})")
    print(f"  {'session':<36} {'human':>6} {'model':>6} {'gap':>5}")
    for gap, session_id, human, model in rows[:limit]:
        print(f"  {session_id:<36} {human:>6} {model:>6} {gap:>5}")


def main():
    verdicts = load_verdicts()
    labels = load_labels()

    if not verdicts:
        print("no verdicts in eval/verdicts/ — generate them first:")
        print("  cd backend && mvn -pl api spring-boot:run "
              "-Dspring-boot.run.profiles=dev,grade-eval")
        return 1

    synthetic = any(lb.get("synthetic") for by in labels.values() for lb in by.values())
    if synthetic:
        print("=" * 78)
        print("SYNTHETIC LABELS — these describe a random number generator, not humans.")
        print("Valid for checking the metric code. Not reportable as agreement or as a ceiling.")
        print("=" * 78)

    print(f"\nverdicts: {len(verdicts)}   labeled sessions: {len(labels)}")

    ceiling = human_ceiling(labels)
    if ceiling:
        print("\nHuman ceiling — labeler A vs labeler B")
        print(f"  QWK          {fmt(ceiling['qwk'])}   <- every model number below is read against this")
        print(f"  exact        {fmt(ceiling['exact'])}")
        print(f"  within +/-1  {fmt(ceiling['within_1'])}")
    else:
        print("\nHuman ceiling: UNAVAILABLE — no sessions rated by two labelers.")
        print("  Without it, an agreement number cannot be interpreted. Say")
        print("  'single-labeler golden set' explicitly rather than quoting a bare kappa.")

    scores = model_scores(verdicts)
    overlap = [s for s in labels if s in scores]
    if not overlap:
        print("\nNo overlap between graded sessions and labeled sessions.")
        print("  The verdicts are from real interviews; the labels are the synthetic fixture.")
        print("  Agreement needs the same sessions on both sides — that is what recruiting is for.")
    else:
        human = [labels[s]["A"]["overall"] for s in overlap if "A" in labels[s]]
        model = [scores[s]["overall"] for s in overlap if "A" in labels[s]]
        m = compare(human, model)
        print(f"\nGrader vs labeler A  (n={m['n']})")
        print(f"  QWK          {fmt(m['qwk'])}      <- headline")
        print(f"  exact        {fmt(m['exact'])}")
        print(f"  within +/-1  {fmt(m['within_1'])}")
        print(f"  MAE          {fmt(m['mae'])}")
        print(f"  bias         {fmt(m['bias'])}      (+ means the model scores higher)")
        print(f"  Spearman     {fmt(m['spearman'])}")
        if ceiling and ceiling["qwk"] and m["qwk"]:
            print(f"  fraction of ceiling  {m['qwk'] / ceiling['qwk']:.2f}")
        report_disagreements(labels, scores)

    report_stability(verdicts)

    print("\nVerdicts produced:")
    for session_id, record in verdicts.items():
        v = record["verdict"]
        dims = " ".join(f"{d['name'][:4]}={d['score']}" for d in v["dimensions"])
        print(f"  {session_id[:12]}  overall={v['overall']} {v['recommendation']:<12} {dims}"
              f"  ({record['gradingMs']} ms)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
