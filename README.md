# AI Digital Interviewer Platform

**▶ [Try the review console](https://yidehao.github.io/ai-digital-interviewer-platform/demo/)** — a
real interview, its real verdict, and the measurements that decided this tool should assist human
review rather than screen candidates.


A video-based AI interviewing system. Recruiters define jobs, digital interviewers (video avatars) and a
question bank in a web admin panel; candidates log in on a mobile app with phone + verification code, watch
the avatar ask each question, and **answer out loud**. Their speech is transcribed with Google Cloud
Speech-to-Text and assessed by a local **Ollama** model.

**There are two interview paths, and a job chooses between them with one column.**

| `job.interview_mode` | What happens |
|---|---|
| `scripted` *(default)* | The original fixed pipeline: three random questions, fixed order, one LLM call at the end that writes an HTML evaluation |
| `agent` | A model-driven loop that decides what to ask next by calling typed tools, asks follow-ups based on what the candidate actually said, and produces a structured verdict with per-dimension evidence |

The agent path is additive. `pages/interviewer.vue` still serves every `scripted` job unchanged, so switching
a job over — and switching it back — is a single `UPDATE`, not a redeploy.

---

## Repository layout

```
AI-interviewer/
├── backend/                  Spring Boot 3.5.9 / Java 21, Maven multi-module API   → :8080 (dev)
│   ├── util/                 enums, response envelope, exception handling, Redis, config beans
│   ├── entity/               entities + BO (request) / VO (response) DTOs
│   ├── dao/                  MyBatis-Plus mappers + hand-written mapper XML
│   ├── service/              business logic, MinIO storage
│   └── api/                  Application, CORS, controllers, OllamaTask, YAML profiles
├── uni-interviewer/          uni-app (Vue 2) candidate app — App / H5 / mini-program
│   └── pages/                interviewer.vue (scripted) + interviewer-agent.vue (agent path)
├── ai-interviewer-frontend/  Vue 2 + Element UI admin panel, static, no build step  → :5500
├── eval/                     evaluation harness — cohort runner, ASR probe, load test, labeling sheet
└── Data/                     Amy/Bob/Cindy/Dave/Emily.mp4 avatar clips, Prompt.docx
```

There is no root-level build; each of the three parts is started independently.

---

## Architecture

```
 ┌──────────────────────────┐          ┌──────────────────────────┐
 │  uni-interviewer         │          │  ai-interviewer-frontend │
 │  (candidate, uni-app)    │          │  (recruiter, Vue2+ElUI)  │
 └────────────┬─────────────┘          └─────────────┬────────────┘
              │  REST / JSON                         │  REST / JSON (axios)
              └──────────────────┬───────────────────┘
                                 ▼
                   ┌─────────────────────────────┐
                   │  backend  (Spring Boot)     │
                   │  controller → service → dao │
                   └──┬────────┬────────┬────────┘
                      │        │        │        └────────────┐
                      ▼        ▼        ▼                     ▼
                   MySQL     Redis    MinIO            Ollama  +  Google STT
                  (:6606)   (:6380)  (:9010)          (:11434)     (cloud)
```

- **MySQL** — all persistent data
- **Redis** — verification codes (10 min TTL) and candidate session tokens (3 h TTL)
- **MinIO** — uploaded avatar images and interview videos
- **Ollama** — local LLM: drives the agent loop and writes the evaluation (`qwen2.5:7b-instruct`, selected by benchmark — a 3B model matched on tool-call emission but chose the right tool 40% of the time against 73%, and complied with a prompt injection)
- **Google Cloud Speech-to-Text** — transcribes the candidate's recorded answers

---

## Interview flow (scripted path)

1. **Login** — `POST /welcome/getSMSCode` generates a 6-digit code and stores it in Redis for 10 minutes.
   There is no SMS provider wired up in this version: the code is **printed to the backend console**.
   `POST /welcome/verify` checks the code, requires the phone number to match a candidate created by the
   recruiter, rejects anyone who already has an interview record (one interview per candidate), and issues
   a 3-hour UUID session token.
2. **Question prep** — `GET /questionLib/prepareQuestion?candidateId=` validates the Redis session, resolves
   candidate → job → digital interviewer, and returns **3 random questions** (question text, reference
   answer, avatar video `aiSrc`).
3. **Interview** — the app plays each question's avatar video with the question overlaid, and records the
   candidate's spoken answer with `uni.getRecorderManager()`.
4. **Transcription** — `POST /speech/uploadVoice` converts the upload to 16 kHz / 16-bit PCM and calls
   Google STT, returning the transcript **and the recogniser's confidence**. Both paths share this step.
5. **Evaluation** — `POST /interviewRecord/collect` hands the answer set to `OllamaTask.display()`, which
   builds the prompt as `job.prompt` + an instruction block that distinguishes the reference answer from
   what the candidate actually said, streams `POST {ollama}/api/generate`, and saves an `interview_record`
   with the transcript, the LLM's verdict, and the total elapsed time.
6. **Review** — the recruiter reads the result in the admin panel under Candidate → Interview Record.

---

## The agent interview path

The scripted flow above asks three questions in a fixed order and grades once at the end. The agent path
replaces the *orchestration*, not the infrastructure — same STT, same question bank, same avatar clips.

### The loop

On each turn the model is given the conversation and the tool catalogue, and must call exactly one tool.
It never writes the interview as prose:

| Tool | What it does |
|---|---|
| `fetch_question` | Draw the next question from the bank, optionally filtered by topic or difficulty |
| `ask_followup` | Probe the previous answer. Capped per question and per session |
| `score_response` | Record a working score for one dimension, with the evidence it rests on |
| `record_evidence` | Quote something worth keeping |
| `run_code` | Execute a candidate's code in a Docker sandbox (`--network=none`, read-only, non-root, pid-capped) |
| `finish_interview` | End it. Termination is an explicit action, not a loop condition |

Arguments and results are validated against **JSON Schema in both directions** — the same twelve schema
files are read by the loop, sent to Ollama as its tool definitions, and published over MCP, so the three
cannot disagree. `additionalProperties: false` is load-bearing: it catches the model inventing fields.

When the model does something unusable, a **nine-rung fallback ladder** handles it deterministically —
prose instead of a tool call, invalid arguments (one repair is offered, which recovers 84% of them),
unknown tool, tool error or timeout, a result that fails *our own* schema, a repeated call, an exhausted
error budget, an exhausted turn budget, and finally an unreachable model. **The last rung degrades to the
scripted pipeline**, so a candidate always finishes an interview.

### Turn-taking

After asking, the loop waits for the answer (`CandidateGate`) and gives up after five minutes with a
`CANDIDATE_TIMEOUT`. This is not incidental — without it the loop asks its next question the instant a tool
returns, and an interview becomes fourteen questions asked to nobody.

### Reaching it from a client

| Transport | For |
|---|---|
| **SSE** — `GET /interview/{candidateId}/stream` | Browsers. Named events: `session`, `question`, `done`, `error` |
| **Polling** — `POST /interview/{candidateId}/start` then `GET /interview/{sessionId}/poll?afterSeq=N` | app-plus and mp-weixin, which have no `EventSource` |

Both read the same session state; the pending question is *computed* from the transcript rather than
replayed from an event queue, so the two transports cannot drift apart.

### Grading

The grader is a separate model call over a **structurally isolated input**. `GradingInput` holds the
transcript, the rubric, the role, the duration and the reference answers — and it *cannot* hold agent state,
because the type has nowhere to put it. A reflection test walks its transitive field types and fails the
build if anything from the agent packages appears. Follow-ups are flattened to plain questions on the way
in: a grader that can see where the interviewer chose to probe is reading the interviewer's opinion of the
candidate.

The verdict is produced by constrained decoding — four dimensions, 1–5, each with its evidence — and stored
structurally in `interview_verdict` rather than as an HTML blob, so scores can be aggregated and compared.
`recommendation` is derived in code from `overall`, because the model agreed with its own score on 1 of 12
verdicts when asked for it directly.

### MCP

The same six tools are published over the Model Context Protocol on both stdio and SSE transports, from the
same schema files, so an external client (Claude Desktop, MCP Inspector) can drive them. This is a second
facade for inspection — live interviews dispatch in-process.

---

## Data model

| Table | Key fields |
|---|---|
| `interviewer` | `id`, `ai_name`, `image` — a digital interviewer (avatar) |
| `job` | `id`, `job_name`, `job_desc`, `status` (1 on / 2 off), `interviewer_id`, `prompt` (LLM prompt prefix used to grade this job) |
| `candidate` | `id`, `real_name`, `identity_num`, `mobile`, `sex`, `face`, `email`, `birthday`, `country`/`state`/`city`/`county`/`address`, `job_id`, `remark` |
| `question_lib` | `id`, `question`, `reference_answer`, `ai_src` (avatar video URL), `interviewer_id`, `is_on` (1/0) |
| `interview_record` | `id`, `candidate_id`, `job_name` (snapshot), `answer_content` (full transcript), `take_time` (seconds), `result` (LLM evaluation) — **scripted path only** |

Added by the agent path (Flyway `V2`–`V5`):

| Table | Key fields |
|---|---|
| `interview_session` | `id`, `candidate_id`, `job_id`, `state`, `terminal_reason` (which rung ended it), `turn_count`, `tool_call_count`, token counts |
| `interview_turn` | `session_id`, `seq`, `kind` (QUESTION / FOLLOWUP / ANSWER / CLOSING), `text`, `stt_confidence`, `ai_src` |
| `tool_invocation` | `session_id`, `seq`, `tool_name`, `args_json`, `args_hash`, `outcome`, `fallback_reason`, `duration_ms` |
| `interview_verdict` | `session_id` (PK), `overall`, `recommendation`, `summary`, `dimensions_json` (four scores with evidence), `graded_ms` |

`tool_invocation` is the table the "adaptive" claim gets checked against: run two candidates who answer
differently and diff their rows. Argument storage is selective — the two tools whose arguments quote the
candidate (`ask_followup.question`, `record_evidence.quote`) store only a hash.

`job` also gains `interview_mode` (`scripted` / `agent`) and `grader_prompt` (the rubric the agent grader
scores against, distinct from `prompt`, which instructs the scripted evaluator).

Relationships: `interviewer` 1—N `job`, `interviewer` 1—N `question_lib`, `job` 1—N `candidate`,
`candidate` 1—1 `interview_record`.

> **Flyway now owns the schema.** `backend/api/src/main/resources/db/migration/` holds `V1__baseline.sql`
> through `V5__interview_verdict.sql`, and migrations run on startup. An existing database is baselined at
> version 1 and only `V2` onwards apply; a fresh empty schema gets the full set. Create the empty database
> and start the app — nothing needs creating by hand.

---

## API reference

All responses use the `GraceJSONResult` envelope: `{ "status": 200, "msg": "OK", "success": true, "data": ... }`.

### Candidate app

| Method | Path | Purpose |
|---|---|---|
| POST | `/welcome/getSMSCode` | Issue a verification code (form param `mobile`); code is logged to the console |
| POST | `/welcome/verify` | Verify code, return candidate profile + session token |
| GET | `/questionLib/prepareQuestion` | 3 random questions for `candidateId` (requires an active session) |
| POST | `/speech/uploadVoice` | Multipart `file` → `{transcript, confidence}` |
| POST | `/interviewRecord/collect` | Submit all answers for LLM evaluation (scripted path) |

### Agent path

| Method | Path | Purpose |
|---|---|---|
| GET | `/interview/{candidateId}/mode` | `{"mode": "agent"}` or `"scripted"` — which page the client should open |
| GET | `/interview/{candidateId}/stream` | Open an SSE stream and start the interview |
| POST | `/interview/{candidateId}/start` | Start without a stream, for clients with no `EventSource` |
| GET | `/interview/{sessionId}/poll?afterSeq=N` | The pending question, or nothing yet |
| POST | `/interview/{sessionId}/answer` | Submit one answer (`turnId`, `transcript`, optional `sttConfidence`) |
| GET | `/actuator/prometheus` | Fallback rungs by reason, tool durations, per-session token counts |

### Admin panel

| Method | Path | Purpose |
|---|---|---|
| POST/GET | `/candidate/createOrUpdate`, `/candidate/list`, `/candidate/detail`, `/candidate/delete` | Candidate management (`list` filters by `realName`, `mobile`, paginated) |
| POST/GET | `/job/createOrUpdate`, `/job/list`, `/job/detail`, `/job/delete`, `/job/nameList` | Job management |
| POST/GET | `/interviewer/createOrUpdate`, `/interviewer/list` | Digital interviewer management |
| POST/GET | `/questionLib/createOrUpdate`, `/questionLib/list`, `/questionLib/show`, `/questionLib/hide`, `/questionLib/delete` | Question bank |
| GET | `/interviewRecord/list` | Interview records, filter by `realName` / `mobile`, paginated |
| POST | `/file/uploadInterviewerImage`, `/file/uploadInterviewVideo` | Upload to MinIO, returns URL |
| GET | `/hello` | Health check |

---

## Prerequisites

| Dependency | Version / notes |
|---|---|
| JDK | 21 |
| Maven | 3.8+ (or the IDE's bundled Maven) |
| MySQL | 8.x, listening on **6606** in the shipped config |
| Redis | listening on **6380**, password `interviewer` |
| MinIO | API on **9010**, bucket `interviewer` |
| Ollama | running locally, with the configured model pulled (`qwen2.5:7b-instruct`) |
| Docker | only for the agent path's `run_code` tool — the sandbox pulls its images at startup |
| Google Cloud | a project with the Speech-to-Text API enabled and a service-account JSON key |
| HBuilderX | to build/run the uni-app candidate client |
| Static server | VS Code **Live Server** (or equivalent) for the admin panel, on port **5500** |

---

## Running it

### 1. Infrastructure

```bash
# MySQL on 6606, Redis on 6380 (password "interviewer"), MinIO on 9010 — however you prefer to run them.
# Then create the schema:
mysql -h 127.0.0.1 -P 6606 -u root -p -e "CREATE DATABASE interviewer DEFAULT CHARSET utf8mb4;"
# Tables are created by Flyway on first start - nothing to run by hand.

# Ollama
ollama serve
ollama pull qwen2.5:7b-instruct   # must match the model in application-dev.yml
ollama list                 # verify
```

### 2. Google credentials

Either set the environment variable:

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/service-account.json
```

or point `google.cloud.credentials.location` at the key in `application-dev.yml`. The env var is used only
when the YAML value is blank. **Do not commit the key file.**

### 3. Backend

```bash
cd backend
mvn clean install -DskipTests
mvn -pl api spring-boot:run        # or run org.interviewer.Application from the IDE
# dev profile → http://localhost:8080
```

### 4. Admin panel

Serve `ai-interviewer-frontend/` as static files and open `pages/a/admin.html` — in VS Code, right-click the
file → *Open with Live Server*. It must be served on **`http://127.0.0.1:5500`**, because that is the single
origin allowed by `CorsConfig` in the dev profile.

The panel needs no build: Vue 2, vue-router, Element UI and axios are loaded from `libs/` via `<script>`
tags, and `.vue` files are fetched at runtime by `httpVueLoader`. The API base URL lives in
`js/request.js` (`http://127.0.0.1:8080`).

### 5. Candidate app

Open `uni-interviewer/` in HBuilderX and run to a device, emulator, or H5.

Set the backend address in `uni-interviewer/App.vue` (`globalData.serverUrl`) to your machine's **LAN IP**,
not `localhost` — a phone or emulator cannot reach the host's loopback address. The same applies to
`minio.fileHost` in `application-dev.yml`, since avatar videos and images are served straight from MinIO to
the device.

### 6. First-run setup in the admin panel

1. **AI Settings → digital interviewer**: create an interviewer, uploading an avatar image (the clips in
   `Data/` are the source material — upload them so `aiSrc` resolves to a MinIO URL).
2. **AI Settings → question library**: add questions, each with a reference answer, an avatar video, and the
   owning interviewer. Enable them.
3. **Job management**: create a job, assign the interviewer, and write the `prompt` used to grade it.
4. **Candidate management**: create a candidate with the phone number they will log in with, assigned to the job.
5. Log in on the candidate app; read the code from the backend console.

---

## Configuration reference

`backend/api/src/main/resources/application.yml` holds shared settings (10 MB upload cap, MyBatis-Plus,
PageHelper) and activates the `dev` profile. Per-profile settings live in `application-dev.yml` (port 8080)
and `application-prod.yml` (port 8099).

| Key | Meaning |
|---|---|
| `interviewer-url.frontend.domain` | The **one** origin allowed by CORS (dev: `http://127.0.0.1:5500`) |
| `minio.endpoint` / `minio.fileHost` | MinIO API URL vs. the host used in returned file URLs (set to a LAN IP) |
| `minio.bucketName` / `accessKey` / `secretKey` | MinIO bucket and credentials |
| `ollama.base-url` / `ollama.model` | Ollama server and model name; the model must appear in `ollama list` |
| `google.cloud.credentials.location` | Absolute path to the service-account JSON |
| `google.cloud.speech.language-code` | Recognition language (`en-US`) |
| `spring.servlet.multipart.max-file-size` | 10 MB — caps both recorded answers and uploaded videos |

---

## Known limitations in this version

- **Secrets are in the config file.** `application-dev.yml` contains database, Redis and MinIO credentials
  and a hardcoded absolute path to a Google service-account key. `application-prod.yml` is listed in
  `.gitignore` but is currently tracked. Move these to environment variables before any shared deployment.
- **No real SMS.** Verification codes are printed to the server console, so login only works for whoever can
  read the backend log.
- ~~`@Async` is not enabled~~ **fixed.** `@EnableAsync` now exists and the stale annotation was removed in
  the same change, so legacy behaviour did not shift silently underneath it.
- ~~Random question selection is unsound~~ **fixed.** `getRandomQuestions` was counting rows filtered by
  `interviewer_id` and then selecting from a *different* result set, so it could return another
  interviewer's questions or `null` entries, one query at a time. It is now a single query that does the
  filtering, randomisation and limit in one place.
- **CORS allows a single origin,** so the H5 build of the candidate app is blocked unless it is served from
  the configured domain. Native builds are unaffected.
- **`uni-interviewer/static/` is empty** and `*.mp4` is git-ignored; avatar media must be uploaded to MinIO
  and referenced by URL.
- **`backend/util/src/main/java/org/interviewer/enums/`** carries unused enums from an unrelated template
  (`OrderStatus`, `PayMethod`, `VIPType`, `ArticleStatus`, …).

### Agent path specifically

- **No production candidate has taken an agent interview yet.** `job.interview_mode` still defaults to
  `scripted`. Both transports have been driven end to end (11 questions / 11 answers over polling, 13 / 13
  over SSE), but by a script, not a person holding a phone.
- **No human-labelled data exists**, so there is no grader-accuracy or human-agreement number. `eval/` is
  built to compute one and deliberately refuses to print a kappa against synthetic labels rather than emit
  a misleading figure. `eval/labeling_sheet.csv` is where that starts.
- **`overall` does not aggregate its own dimensions.** A live verdict came back `overall=3` with all four
  dimensions at 2. Deriving it in code — as `recommendation` already is — would invalidate every existing
  number computed on model-produced `overall`, so it belongs in a measured before/after.
- **The concurrency figure is app-tier only.** 48 concurrent sessions per node with the model stubbed and no
  answer waiting. `EmitterRegistry` and the live-session map are both node-local, so any deployment needs
  sticky sessions and the number is strictly **per node**.
- **The `sttConfidence` threshold (0.85) is a default, not a calibration.** ASR confidence and word error
  rate are different quantities and the mapping has not been established.

---

## Evaluation

`eval/` holds the measurement harness. It is deliberately conservative about what it will report:

| Script | What it answers |
|---|---|
| `analyse_cohort.py` | Does the grader rank by competence, and does *phrasing* move the score independently of it? 3 quality tiers x 4 surface profiles, facts held constant within a tier |
| `compare_runs.py` | Which findings survive a second run — anything that moves between runs cannot be reported |
| `asr_probe.py` | Word error rate through the real STT path |
| `loadtest.py` | Concurrent sessions per node, app tier, model stubbed |
| `harness/interview.html` | A browser client for running real sessions: streams, records 16 kHz WAV, transcribes, submits |

The most useful thing it has found is not an accuracy number. It is that **the grader scores identical
technical content differently depending on phrasing** — a designed-strong candidate using non-native
phrasing lost a point on all four dimensions while a weak one lost nothing, so the penalty scales with
competence. Reproduced across two runs before being believed.

---

## Further reading

- `ai-interviewer-frontend/README.md` — detailed walkthrough of the build-free admin SPA (routing, API
  wrappers, `httpVueLoader`).
- `uni-interviewer/README.md` — page-by-page description of the candidate app and its utilities.
