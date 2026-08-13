# AI Digital Interviewer Platform

A video-based AI interviewing system. Recruiters define jobs, digital interviewers (video avatars) and a
question bank in a web admin panel; candidates log in on a mobile app with phone + verification code, watch
the avatar ask each question, and **answer out loud**. Their speech is transcribed with Google Cloud
Speech-to-Text, and a local **Ollama** LLM scores each answer against the reference answer using a
per-job prompt. The written evaluation lands back in the admin panel as an interview record.

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
├── ai-interviewer-frontend/  Vue 2 + Element UI admin panel, static, no build step  → :5500
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
- **Ollama** — local LLM that writes the evaluation (`llama3.2:3b` by default)
- **Google Cloud Speech-to-Text** — transcribes the candidate's recorded answers

---

## Interview flow

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
4. **Transcription** — `POST /speech/uploadVoice` converts the uploaded MP3 to 16 kHz / 16-bit PCM
   (`mp3spi`) and calls Google STT, returning the transcript.
5. **Evaluation** — `POST /interviewRecord/collect` hands the answer set to `OllamaTask.display()`, which
   builds the prompt as `job.prompt` + an instruction block that distinguishes the reference answer from
   what the candidate actually said, streams `POST {ollama}/api/generate`, and saves an `interview_record`
   with the transcript, the LLM's verdict, and the total elapsed time.
6. **Review** — the recruiter reads the result in the admin panel under Candidate → Interview Record.

---

## Data model

| Table | Key fields |
|---|---|
| `interviewer` | `id`, `ai_name`, `image` — a digital interviewer (avatar) |
| `job` | `id`, `job_name`, `job_desc`, `status` (1 on / 2 off), `interviewer_id`, `prompt` (LLM prompt prefix used to grade this job) |
| `candidate` | `id`, `real_name`, `identity_num`, `mobile`, `sex`, `face`, `email`, `birthday`, `country`/`state`/`city`/`county`/`address`, `job_id`, `remark` |
| `question_lib` | `id`, `question`, `reference_answer`, `ai_src` (avatar video URL), `interviewer_id`, `is_on` (1/0) |
| `interview_record` | `id`, `candidate_id`, `job_name` (snapshot), `answer_content` (full transcript), `take_time` (seconds), `result` (LLM evaluation) |

Relationships: `interviewer` 1—N `job`, `interviewer` 1—N `question_lib`, `job` 1—N `candidate`,
`candidate` 1—1 `interview_record`.

> **No schema migration script ships with this version.** The tables above must be created manually in the
> `interviewer` database before first run. Timestamp columns are `create_time`/`created_time` and
> `updated_time` — see each entity class in `backend/entity/src/main/java/org/interviewer/entity/` for the
> exact per-table naming, and the mapper XML in `backend/dao/src/main/resources/mapper/` for column names.

---

## API reference

All responses use the `GraceJSONResult` envelope: `{ "status": 200, "msg": "OK", "success": true, "data": ... }`.

### Candidate app

| Method | Path | Purpose |
|---|---|---|
| POST | `/welcome/getSMSCode` | Issue a verification code (form param `mobile`); code is logged to the console |
| POST | `/welcome/verify` | Verify code, return candidate profile + session token |
| GET | `/questionLib/prepareQuestion` | 3 random questions for `candidateId` (requires an active session) |
| POST | `/speech/uploadVoice` | Multipart `file` (MP3) → transcript string |
| POST | `/interviewRecord/collect` | Submit all answers for LLM evaluation |

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
| Ollama | running locally, with the configured model pulled |
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
# ...and create the five tables listed under "Data model".

# Ollama
ollama serve
ollama pull llama3.2:3b     # must match ollama.model in application-dev.yml
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
- **`@Async` is not enabled.** `OllamaTask.display()` is annotated `@Async`, but no `@EnableAsync` exists
  anywhere in the project, so evaluation runs **on the request thread**: `POST /interviewRecord/collect`
  blocks until Ollama finishes (read timeout 600 s). Add `@EnableAsync` to `Application` to get the intended
  fire-and-forget behaviour — and note that the candidate then gets no signal about whether the analysis
  actually succeeded; failures surface only in the log.
- **Random question selection is unsound.** `getRandomQuestions` counts rows filtered by `interviewer_id`,
  then picks rows with `SELECT * FROM question_lib WHERE is_on=1 LIMIT #{index}, 1` — a different result set.
  Questions belonging to another interviewer can be returned, and a disabled question can shift the offsets
  enough to yield `null` entries. It also issues one query per question (N+1).
- **CORS allows a single origin,** so the H5 build of the candidate app is blocked unless it is served from
  the configured domain. Native builds are unaffected.
- **`uni-interviewer/static/` is empty** and `*.mp4` is git-ignored; avatar media must be uploaded to MinIO
  and referenced by URL.
- **`backend/util/src/main/java/org/interviewer/enums/`** carries unused enums from an unrelated template
  (`OrderStatus`, `PayMethod`, `VIPType`, `ArticleStatus`, …).

---

## Further reading

- `ai-interviewer-frontend/README.md` — detailed walkthrough of the build-free admin SPA (routing, API
  wrappers, `httpVueLoader`).
- `uni-interviewer/README.md` — page-by-page description of the candidate app and its utilities.
