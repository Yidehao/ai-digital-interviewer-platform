# Deriving `overall` from the dimensions — measured before/after

`overall` used to be a schema field the model produced. It is now computed from the four dimension
scores the model already gave. This is the same lever as `recommendation`, which was moved into code
after the model kept it consistent with `overall` on 1 of 12 verdicts.

## Why, and how the defect was found

**A real interview found it.** The model returned `overall = 3` while scoring the four dimensions
**4, 4, 4, 3**. A human reviewer, working through the review console — which shows the transcript and
takes the reviewer's scores *before* revealing the model's — independently produced **the same four
dimension scores** and an overall of **4**.

They agreed about everything they had actually looked at. They disagreed only about arithmetic the
model should never have been asked to do.

The mechanism was visible in the schema. `overall` sat **before** `dimensions`, and constrained
decoding emits fields in schema order — so the model chose a headline number before scoring a single
dimension or writing one word of evidence, then rationalised underneath it. That is the exact
inversion of the evidence → reasoning → score ordering enforced *within* each dimension, which
works.

Both problems are fixed by removing the field: nothing is committed early, and nothing can
contradict itself.

## The measurement

| | model-produced overall | derived overall |
|---|---|---|
| **Spearman vs designed rank** | 0.79 | **0.84** |
| **strong − weak gap** | +1.50 | **+1.75** |
| weak mean | 2.00 | 1.75 |
| mixed mean | 3.00 | **2.25** |
| strong mean | 3.50 | 3.50 |
| distinct values used | 4 (1–4) | 4 (1–4) |

Construct validity improved and the tiers separated further. The mixed tier moved most: it had been
pinned at exactly 3.00 for every one of its four participants, and now sits at 2.25 with variation
inside it.

`overallFrom` is an **unweighted mean, rounded half-up**. Weighting correctness above communication
is defensible and is a *policy* decision belonging to whoever owns the rubric — not a default buried
in a grader. `DerivedOverallTest` checks exhaustively over all 5⁴ dimension combinations that the
overall always lies within the range of the dimensions printed beside it.

## The finding that matters most, and it is uncomfortable

**The surface penalty is now visible at the overall level, where it previously was not.**

Delta against `neutral` phrasing of the same tier, `[weak/mixed/strong]`:

| | model-produced overall | derived overall |
|---|---|---|
| non-native, on **overall** | +0.00 `[0/0/0]` | **−0.67** `[−1/0/−1]` |

This is not the bias getting worse. **The bias was always in the dimensions** — the correctness
penalty of −1 at every tier has been reproduced across every run since it was found. What changed is
that the model's disconnected `overall` was *masking* it: a headline number chosen before the
dimensions existed could not propagate a penalty the dimensions contained.

So one of the reassuring numbers in the previous report was an artifact. "Non-native phrasing costs
nothing at the overall level" was true only because the overall was not a function of anything.
Making the verdict internally coherent made its unfairness legible, which is the correct direction
even though it reads worse.

## Caveats

**One run.** The two runs before this were identical to each other, so movement is unlikely, but the
project's standard is that a single-run delta is provisional and this one has not been repeated.

**Every previously reported number computed on `overall` is superseded.** Re-run against the same
two labelers:

| grader vs | model-produced overall | derived overall |
|---|---|---|
| labeler A | QWK 0.56, bias +0.33 | **QWK 0.64**, bias +0.67 |
| labeler B | QWK 0.58, bias +0.42 | **QWK 0.59**, bias +0.75 |

Agreement improved, most clearly against labeler A. The **bias roughly doubled** — the grader now
scores further above both humans than it did. Those move in opposite directions and both are real:
the ranking is better, and the calibration is worse, because an unweighted mean of four dimensions
that each cluster high produces an overall that clusters high.

The human ceiling of 0.97 is untouched, as it must be — it is computed between the two labelers and
never involved the model at all.

**The gap is still the story.** 0.59–0.64 against a ceiling of 0.97 is an improvement in a number
that was never close.

## Reproducing

```bash
cd backend && mvn -pl api spring-boot:run -Dspring-boot.run.profiles=dev,cohort
python3 eval/analyse_cohort.py
python3 eval/compare_runs.py --a cohort_results_modeloverall.json --b cohort_results.json \
    --label-a "model-produced overall" --label-b "derived overall"
```
