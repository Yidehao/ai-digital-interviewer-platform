#!/usr/bin/env python3
"""
Agreement metrics for ordinal rubric grading.

Pure Python, no numpy or scipy — this has to run wherever the eval does, and the maths is
small enough that a dependency costs more than it saves.

Which metric to lead with, and why:

  Quadratic weighted kappa (QWK) is the headline. It is the standard for ordinal rubric
  scoring: it corrects for agreement that would happen by chance, and it penalises a 2-vs-5
  disagreement far more than a 3-vs-4. Plain percent agreement does neither, which is why a
  grader that always says "3" can look impressive on a dataset where most answers are average.

  Everything else is diagnostic. MAE catches systematic leniency. Spearman answers a different
  and sometimes more useful question — does it *rank* candidates the same way, even if its
  absolute scores are shifted? A grader with a constant +1 bias has poor MAE and perfect
  Spearman, and for a recruiter ranking a shortlist that may be fine.

  Report ±1 accuracy too, because it is the number non-specialists actually understand.

Nothing here is meaningful without a comparison point. QWK 0.60 against human labels is
excellent if two humans only reach 0.62 with each other, and poor if they reach 0.85. Always
compute the human ceiling on the same scale from the same data.
"""

import json
import sys
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).parent
SCALE = [1, 2, 3, 4, 5]
DIMENSIONS = ["correctness", "depth", "communication", "practical_experience"]


# --------------------------------------------------------------------------- metrics

def quadratic_weighted_kappa(a, b, scale=SCALE):
    """QWK between two equal-length sequences of ordinal ratings."""
    if not a:
        return None
    k = len(scale)
    idx = {v: i for i, v in enumerate(scale)}

    observed = [[0.0] * k for _ in range(k)]
    for x, y in zip(a, b):
        observed[idx[x]][idx[y]] += 1

    hist_a = [0.0] * k
    hist_b = [0.0] * k
    for x, y in zip(a, b):
        hist_a[idx[x]] += 1
        hist_b[idx[y]] += 1

    n = float(len(a))
    expected = [[hist_a[i] * hist_b[j] / n for j in range(k)] for i in range(k)]

    denom = (k - 1) ** 2
    num = den = 0.0
    for i in range(k):
        for j in range(k):
            w = ((i - j) ** 2) / denom
            num += w * observed[i][j]
            den += w * expected[i][j]
    if den == 0:
        # Both raters used exactly one category. Agreement is total but chance-corrected
        # agreement is undefined, and returning 1.0 here would flatter a degenerate case.
        return None
    return 1.0 - num / den


def exact_agreement(a, b):
    return sum(1 for x, y in zip(a, b) if x == y) / len(a) if a else None


def within_one(a, b):
    return sum(1 for x, y in zip(a, b) if abs(x - y) <= 1) / len(a) if a else None


def mean_absolute_error(a, b):
    return sum(abs(x - y) for x, y in zip(a, b)) / len(a) if a else None


def mean_bias(a, b):
    """Positive means b scores higher than a on average — catches systematic leniency."""
    return sum(y - x for x, y in zip(a, b)) / len(a) if a else None


def _ranks(values):
    """Fractional ranks, ties averaged."""
    order = sorted(range(len(values)), key=lambda i: values[i])
    ranks = [0.0] * len(values)
    i = 0
    while i < len(order):
        j = i
        while j + 1 < len(order) and values[order[j + 1]] == values[order[i]]:
            j += 1
        avg = (i + j) / 2.0 + 1.0
        for t in range(i, j + 1):
            ranks[order[t]] = avg
        i = j + 1
    return ranks


def spearman(a, b):
    if len(a) < 2:
        return None
    ra, rb = _ranks(a), _ranks(b)
    ma = sum(ra) / len(ra)
    mb = sum(rb) / len(rb)
    num = sum((x - ma) * (y - mb) for x, y in zip(ra, rb))
    da = sum((x - ma) ** 2 for x in ra) ** 0.5
    db = sum((y - mb) ** 2 for y in rb) ** 0.5
    return num / (da * db) if da and db else None


def compare(a, b):
    """All metrics for one pair of aligned rating sequences."""
    return {
        "n": len(a),
        "qwk": quadratic_weighted_kappa(a, b),
        "exact": exact_agreement(a, b),
        "within_1": within_one(a, b),
        "mae": mean_absolute_error(a, b),
        "bias": mean_bias(a, b),
        "spearman": spearman(a, b),
    }


# --------------------------------------------------------------------------- loading

def load_labels(labels_dir=None):
    """{sessionId: {labeler: label}}"""
    labels_dir = Path(labels_dir or HERE / "labels")
    out = defaultdict(dict)
    for p in sorted(labels_dir.glob("*.json")):
        lb = json.loads(p.read_text())
        out[lb["sessionId"]][lb["labeler"]] = lb
    return out


def paired(labels, left, right, field="overall", dimension=None):
    """Aligned rating sequences for two raters over the sessions they both rated."""
    a, b = [], []
    for _, by_rater in sorted(labels.items()):
        if left in by_rater and right in by_rater:
            if dimension:
                a.append(by_rater[left]["dimensions"][dimension])
                b.append(by_rater[right]["dimensions"][dimension])
            else:
                a.append(by_rater[left][field])
                b.append(by_rater[right][field])
    return a, b


def fmt(v):
    return "—" if v is None else f"{v:.2f}" if isinstance(v, float) else str(v)


def report_pair(labels, left, right, title):
    a, b = paired(labels, left, right)
    if not a:
        print(f"\n{title}: no sessions rated by both {left} and {right}")
        return
    m = compare(a, b)
    print(f"\n{title}  (n={m['n']} sessions)")
    print(f"  QWK          {fmt(m['qwk'])}      <- headline")
    print(f"  exact        {fmt(m['exact'])}")
    print(f"  within +/-1  {fmt(m['within_1'])}")
    print(f"  MAE          {fmt(m['mae'])}")
    print(f"  bias         {fmt(m['bias'])}      (+ means {right} scores higher)")
    print(f"  Spearman     {fmt(m['spearman'])}")
    print("  per dimension:")
    for d in DIMENSIONS:
        da, db = paired(labels, left, right, dimension=d)
        dm = compare(da, db)
        print(f"    {d:<22} QWK {fmt(dm['qwk']):>6}   MAE {fmt(dm['mae']):>5}   "
              f"bias {fmt(dm['bias']):>6}")


def main():
    labels = load_labels()
    if not labels:
        print("no labels found in eval/labels/ — run make_synthetic_golden.py first")
        return 1

    synthetic = any(lb.get("synthetic")
                    for by in labels.values() for lb in by.values())
    if synthetic:
        print("=" * 78)
        print("SYNTHETIC DATA — these numbers describe a random number generator, not humans.")
        print("Valid for checking that the metrics work. Not reportable as a human ceiling.")
        print("=" * 78)

    report_pair(labels, "A", "B", "Human ceiling — labeler A vs labeler B")
    if any("model" in by for by in labels.values()):
        report_pair(labels, "A", "model", "Grader vs labeler A")
    return 0


if __name__ == "__main__":
    sys.exit(main())
