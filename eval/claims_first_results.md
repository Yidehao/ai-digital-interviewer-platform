# Claims-first grading + anchored rubric — measured twice

Two interventions, applied together and measured against the same twelve-participant cohort:

1. **`claims` first in the verdict schema** — the model extracts every checkable technical assertion,
   restated plainly, *before* any score exists. Constrained decoding emits fields in schema order,
   so this forces the technical content to be separated from the prose that carried it.
2. **Anchored rubric (V7)** — concrete text for what a 2 and a 4 look like on each dimension, because
   stating a range did not produce one.

Run twice. **Both runs came back identical**, so everything below is reproduced, not a single draw.

## What improved

| | V3 rubric, no claims | V7 anchored + claims-first |
|---|---|---|
| Spearman vs designed rank | 0.71 | **0.79** |
| Distinct overall values used | 2 (only 2–3) | **4 (1–4)** |
| strong − weak gap | +0.75 | **+1.50** |
| mixed vs strong means | 3.00 / 3.00 — **no separation** | 3.00 / **3.50** |
| Identical across two runs | 9/12 | **12/12** |

The scale compression is substantially better. The instrument previously could not tell a *mixed*
candidate from a *strong* one at all — both averaged exactly 3.00, and that is the distinction a
hiring decision actually turns on. It now separates them.

Reproducibility went from 3 of 12 moving between identical runs to 0 of 12. Two runs is thin
evidence for a stability claim, and the deployed path does not rely on it either way: grading
samples three times and escalates any disagreement.

## What did not improve — and this is the finding that matters

**Non-native phrasing still costs 1 point of correctness, at every competence tier, in both runs.**

```
findings that hold in BOTH runs
  non-native phrasing:
    strong   correctness -1
    mixed    correctness -1
    weak     correctness -1, depth -1, communication -1
```

The claims-first change was the most promising available lever — the specific hypothesis was that a
grader reading correctness straight off a transcript scores the claims and the prose together, so
extracting the claims first should decouple them. **It did not.** The penalty is, if anything, now
more clearly established: by this harness's own criterion, a delta appearing at *all three tiers* is
a property of the grader rather than noise, and before the change it appeared at one or two.

The shape did change. The *overall* score for a designed-strong non-native candidate is no longer
depressed, and the penalty is now concentrated in correctness instead of spread across four
dimensions. That is a smaller and better-understood defect. It is not an absent one.

**A candidate's technical claims are being marked down for the grammar around them, on a dimension
that is definitionally about whether the claims are true.** No rubric wording and no schema ordering
has removed that, and both have now been tried.

## What follows

This is the measurement that decides whether the system may gate anyone, and it says no. The
grader is a usable screening aid — it ranks by competence at ρ=0.79 and it now uses the scale — and
it is not an instrument that should reject a person, because it applies a systematic penalty to a
protected-adjacent characteristic on the dimension least entitled to see it.

The code reflects that: `GradingOutcome.advisory` is always true with no path that sets it false,
`interview_verdict.advisory` is `NOT NULL DEFAULT 1`, and `needs_human_review` defaults to 1.

**Still missing, and it bounds every claim here:** no human labels. Twelve authored transcripts
graded by one model tell you about the model, not about agreement. A −1 correctness penalty is
damning if human interviewers show none and merely *typical* if they show the same — and human
interviewers are known to have this bias too. `eval/labeling_sheet.csv` is the only thing that
resolves it, and it needs two people.

## Reproducing

```bash
cd backend && mvn -pl api spring-boot:run -Dspring-boot.run.profiles=dev,cohort   # ~11 min
python3 eval/analyse_cohort.py
python3 eval/compare_runs.py --a cohort_results_claims_run1.json --b cohort_results.json \
    --label-a "run 1" --label-b "run 2"
```

Note the eval path calls `gradeOnce()`, not `grade()`. Sampling three times here would suppress the
run-to-run movement this harness exists to detect and report a stability the deployed system does
not have.
