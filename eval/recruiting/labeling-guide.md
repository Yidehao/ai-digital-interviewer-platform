# Labeling guide

*Hand this to the second labeler. It should be readable without knowing anything about the
project.*

---

## What you're doing and why it matters

You'll read 10 interview transcripts and score each on four dimensions. An AI grader has
already scored the same transcripts, and so have I.

The point is **not** to check whether you agree with the AI. It's to establish how much two
people disagree with each other on the same transcript. Human graders never agree perfectly —
and until we know how much *humans* differ, the AI's agreement score means nothing. If you and
I agree 62% of the time and the AI agrees with me 60% of the time, the AI is doing well. Without
your numbers, 60% is impossible to interpret.

So: **score them the way you actually see them.** If you think an answer is weak and the AI
called it strong, that disagreement is the data. Don't try to guess what anyone else said.

About an hour. Do it in one sitting if you can — standards drift across days.

---

## The four dimensions

Score each from **1 to 5**. Score each **independently** — a candidate can communicate
beautifully while being wrong.

| Dimension | The question it answers |
|---|---|
| **Correctness** | Is what they said technically accurate? Would it work? |
| **Depth** | Did they go past the surface — tradeoffs, edge cases, *why* rather than *what*? |
| **Communication** | Was it structured and followable? Could a colleague act on it? |
| **Practical experience** | Does this sound like someone who has actually done it, or someone who has read about it? |

### The scale

| | |
|---|---|
| **1** | Wrong, absent, or actively misleading |
| **2** | Substantially incomplete or largely superficial |
| **3** | Adequate. Answers the question, nothing beyond it |
| **4** | Strong. Goes beyond the question, shows judgment |
| **5** | Excellent. Would raise this answer in a hiring debrief |

Use the whole scale. **3 is a real score, not a hedge** — most answers are 3s, and a set of
labels where everything is 3 or 4 carries almost no information.

---

## Also record

**Overall** (1–5) — your holistic read. Not an average of the four; if one dimension dominates
your impression, let it.

**Recommendation** — `strong_no` / `no` / `lean_yes` / `yes` / `strong_yes`. The question is
*"would you advance this person to the next round?"*, not *"would you hire them?"*

**Notes** — one line on anything that made scoring hard. Genuinely useful: ambiguous questions,
transcription that mangled a technical term, an answer that was strong on something the rubric
doesn't cover.

---

## Things worth knowing before you start

**These are speech transcripts, not writing.** Expect false starts, filler, and sentences that
never finish. Score the thinking, not the fluency — spoken answers look worse on the page than
they sounded.

**Transcription errors are not the candidate's fault.** If a technical term came out garbled,
read past it. Flag it in the notes.

**Don't reward length.** Longer answers tend to score higher for no good reason, and we're
specifically measuring whether that bias exists. A tight 3-sentence answer can be a 5.

**Don't compare candidates to each other.** Score each against the rubric on its own. You'll
read them in a random order, and a strong transcript right after a weak one distorts both.

**Don't look up the reference answers**, even if you find them. Score what the candidate said.

---

## Format

One JSON file per transcript in `eval/labels/`, or the spreadsheet — whichever I've sent you:

```json
{
  "sessionId": "session-P07",
  "labeler": "B",
  "overall": 4,
  "recommendation": "yes",
  "dimensions": {
    "correctness": 4,
    "depth": 3,
    "communication": 5,
    "practical_experience": 4
  },
  "notes": "Strong on incident response, thin on prevention. STT mangled 'idempotent' twice."
}
```

Ask me anything while you're going — a question about the rubric is much cheaper than ten
labels scored under a misunderstanding.
