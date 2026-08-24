# Human ceiling, and the question it settles

Two labelers, twelve transcripts, blinded to tier and surface profile. This is the number every
other number in the project has been waiting on.

## 1. The ceiling

| | QWK | exact | within ±1 |
|---|---|---|---|
| **overall** | **0.97** | 0.92 | 1.00 |
| correctness | 0.79 | 0.33 | 1.00 |
| depth | 1.00 | 1.00 | 1.00 |
| communication | 0.94 | 0.92 | 1.00 |
| practical_experience | 1.00 | 1.00 | 1.00 |

## 2. The grader, read against it

| | QWK | exact | within ±1 | bias |
|---|---|---|---|---|
| grader vs labeler A | 0.56 | 0.67 | 0.83 | +0.33 |
| grader vs labeler B | 0.58 | 0.58 | 0.83 | +0.42 |

**QWK 0.56–0.58 against a ceiling of 0.97.** This is the case the ceiling was needed to
distinguish, and it came out the unfavourable way: the grader is not "as good as humans manage on a
hard rubric". Humans agree far more with each other than the grader agrees with either. The positive
bias means it also scores consistently *higher* than both.

## 3. The surface penalty — settled

Delta against the `neutral` phrasing of the same tier. The technical facts are identical within a
tier, so any non-zero value is a response to phrasing alone. `[strong/mixed/weak]`:

**correctness, non-native phrasing**

| | mean | per tier |
|---|---|---|
| labeler A | **+0.00** | [0 / 0 / 0] |
| labeler B | **+0.00** | [0 / 0 / 0] |
| **grader** | **−1.00** | **[−1 / −1 / −1]** |

**Neither human docked a single point of correctness for non-native phrasing, at any tier. The
grader docked one at every tier.** This is a defect the humans do not have — not a shared human
bias the model inherited, which was the outcome that would have made it merely typical.

The humans are not indifferent to surface generally. Both penalised **verbose** phrasing on
communication (−1.33 mean, both labelers) and the grader *rewarded* it (+0.33). So the two
disagree about surface in both directions: humans mark down padding, the grader marks down accent.

## Two caveats that bound all of the above

**A QWK of 0.97 between two independent raters is implausibly high.** Interview-rubric agreement
between humans is normally 0.5–0.75. Depth and practical_experience came back at exactly 1.00 —
perfect agreement on all twelve. On n=12 with a narrow score range QWK is unstable, but this is
still far outside what independent labeling usually produces. Treat 0.97 as an upper bound on the
ceiling, not a measurement of it, and the grader's gap as correspondingly uncertain.

**The blinding failed by construction, and the notes prove it.** Six of twenty-four notes reference
"non-native grammar not penalized" or "content identical to D and F". The cohort holds facts
constant across surface variants, so an attentive labeler notices the triplets, infers what is being
tested, and can then consciously avoid the behaviour under study. Labelers primed not to penalise
non-native phrasing will not penalise it — which is exactly the result observed.

That does not make the grader's −1 disappear: it is measured on the model, reproduced across two
runs, and independent of what humans did. It does weaken *"humans show none"* into *"humans who have
noticed the manipulation show none."*

**What would fix it:** a between-subjects design — each labeler sees one surface variant per tier
and never the triplet, so the manipulation is invisible. That needs roughly three times as many
labelers to get the same number of observations per cell, which is the honest cost of the stronger
design.

## What this supports saying

> Two labelers agreed with each other at QWK 0.97 on overall score. The grader agreed with them at
> 0.56–0.58. Neither human docked correctness for non-native phrasing; the grader docked a point at
> every competence tier. On a small blinded set with caveats about labeler independence, the grader
> is measurably worse than the humans it would replace, and wrong in a specific and predictable
> direction.

**Not** "human-benchmarked accuracy of X%". Twelve authored transcripts and two labelers, one of
whom is the system's author, is a pilot. It is enough to decide deployability — and it decides it
against.
