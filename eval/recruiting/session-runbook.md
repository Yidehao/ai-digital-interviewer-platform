# Session runbook — 30 minutes per participant

Same script every time. Consistency is not pedantry here: variation in how sessions are run
becomes variation in the transcripts, which becomes noise in the benchmark that cannot be
separated from real grading disagreement afterwards.

---

## Before they arrive (5 min, once per day)

```bash
# services
docker start <mysql> <redis> <minio>          # 6606 / 6380 / 9010
ollama serve                                   # only needed if grading during the session

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
export GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/key.json
cd backend && mvn -pl api spring-boot:run
```

Then, per participant, create their candidate record so login works — `mobile` is what they
log in with, and `identity_num` is `NOT NULL UNIQUE` so it must be set:

```bash
curl -s -X POST http://localhost:8080/candidate/createOrUpdate \
  -H 'Content-Type: application/json' \
  -d '{"realName":"P07","identityNum":"P07","mobile":"5550000007",
       "jobId":"<your job id>"}'
```

Use a **pseudonym from the start** (`P07`, not their name). Anonymising later means editing
transcripts; anonymising now means never having the problem.

Check the question bank has enough enabled questions for the interviewer assigned to that job —
`prepareQuestion` asks for 3 and returns fewer if the bank is smaller.

---

## 1 · Consent (2 min) — say it out loud, every time

> Before we start, three things.
>
> **One:** I'm recording your voice and transcribing it. The audio stays on my laptop and never
> goes into the repository.
>
> **Two:** the transcript — what you said, as text — gets anonymised and may end up in a public
> GitHub repo as test data. I'll show you yours before I commit anything. Your name won't be on
> it; if you mention a company or a project, I'll strip that too.
>
> **Three:** you can stop at any point, and you can ask me to delete your data later with no
> explanation needed.
>
> Any questions? Are you OK to go ahead?

Record the answer in `participants.csv`. If they hesitate at all, offer the transcript-review
step before committing — it costs nothing and it is the thing people actually worry about.

---

## 2 · Framing (2 min)

> This is a mock technical interview. You'll get 3 to 5 questions, spoken, and you answer out
> loud like a real interview. There's no follow-up and no interruption — the system just asks,
> you answer, you move on.
>
> Answer the way you would in a real interview. **Don't try to be a good test case.** If you
> don't know something, say you don't know — that's genuinely useful data and it's the kind of
> answer I have the least of.

That last instruction matters more than it sounds. Participants who know they are testing a
grader tend to give unnaturally complete, well-structured answers, and a benchmark built only
from those measures nothing — clean answers are easy to grade and every grader agrees on them.

---

## 3 · The interview (15 min)

They use the app. You stay quiet. Do not coach, react, or fill silences — an interviewer
nodding along changes the answers.

Note in `participants.csv` anything unusual: a long pause, an interruption, a mangled
transcription, a question they misheard. That context is what lets you interpret a
disagreement later instead of guessing at it.

---

## 4 · Feedback (8 min) — you promised this, so deliver it

Talk through their answers yourself. Be specific and useful:

- what was strong, quoted back to them
- what was thin, and what a stronger answer adds
- one concrete thing to practise

Do **not** show them a model-generated score. It invites arguing with the number, and the
number isn't validated yet — that's the entire point of what you're collecting.

---

## 5 · The second-labeler ask (2 min)

Ask now, in person, not later by message. Wording is in `recruitment-message.md`. Only one
person across all ten sessions needs to say yes.

---

## 6 · After they leave (3 min)

```bash
# audio out of MinIO and into the gitignored folder
mkdir -p eval/audio/P07
# export the transcript with its pseudonym
```

Then:

- record the session in `participants.csv`
- read the transcript once and strip anything identifying — names, employers, project names,
  university, anything distinctive enough to identify them
- save to `eval/golden/session-P07.json`
- **label it yourself the same day**, while you still remember the session. Labels written a
  week later are worse, and inconsistently worse, which is the bad kind.

---

## Scheduling

Two per day maximum. Three back-to-back sessions and the feedback gets perfunctory, which is
the part participants are actually there for — and word travels.
