# Recruiting participants for the golden set

**Start now, during Phase 1.** This is the only part of the project whose duration is set by
other people's calendars rather than by how fast code gets written. Everything else can be
compressed; this cannot.

## Why it can start before any agent code exists

The golden set does not need the agent. The grader does not care whether a human, a script, or
a model chose the questions — it reads a transcript and a rubric. And the ASR path is identical
either way.

**The existing app already produces exactly what is needed**, and has since Phase 0: real
spoken answers, real speech-to-text output, real hesitation and tangents and half-finished
reasoning. Only one narrow piece — the agent arm of the scripted-vs-agent comparison — has to
wait for the eval harness after Phase 6, and it can reuse people who have already sat a session.

## What one 30-minute sitting yields

| Output | Used by |
|---|---|
| Interview transcript + human label | The golden set — the QWK benchmark |
| The raw audio | The ASR-path evaluation (WER, and the kappa delta between clean and ASR text) |
| A possible second labeler | The human ceiling, which makes every agreement number interpretable |

## Target

**10 participants**, and **one of them** willing to label 10 transcripts afterwards.

On counts: 10 people × one session = 10 transcripts. If a "40-session golden set" is wanted,
that means **3 sessions per person on non-overlapping question subsets**, not 10 real sessions
padded with 30 self-written ones. Decide which before collection starts, and report whichever
number is true. A 15-session golden set built from real speech is more credible than 40 sessions
where most were written by the person who wrote the rubric — that only measures self-consistency.

## The second labeler is the cheapest thing in the entire eval plan

One person, one hour, on transcripts that already exist. It costs less than recruiting a single
extra participant, and it buys the answer to the hardest question anyone will ask about this
project: *"your grader agrees with humans at 0.6 — is that good?"*

Without a human ceiling that number sounds mediocre. With a ceiling of 0.62 it is excellent.

**Ask at the end of each session, while the person is still engaged.** Only one has to say yes.

## Files here

| File | Use |
|---|---|
| `recruitment-message.md` | Copy-paste text for a group chat or DM |
| `session-runbook.md` | What to say and do during the 30 minutes, including the consent script |
| `labeling-guide.md` | Hand to the second labeler; the rubric and instructions |
| `participants.csv` | Tracking template — who, when, what was collected, consent recorded |

## Data handling — decide before the first session, not after

Real classmates, real voices, real answers about their technical ability, in a repository that
may become public.

- **State it out loud at the start of every session**: what is recorded, where it is stored, and
  that transcripts may appear in a public repository.
- **Audio never enters git.** `eval/audio/` is already in `.gitignore`.
- **Strip names and identifying details from transcripts before committing.** Employer names and
  project names count as identifying.
- Anyone who wants their data removed later gets it removed, without discussion.
