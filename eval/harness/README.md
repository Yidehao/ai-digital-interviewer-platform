# eval/harness/

The permanent eval client. Ugly on purpose.

| File | What it is |
|---|---|
| `interview.html` | the agent interview client — stream, record, transcribe, submit |
| `audio-probe.html` | Phase 0 probe that established which audio formats the server accepts |

## Running a session

```bash
# 1. backend
cd backend && mvn -pl api spring-boot:run -Dspring-boot.run.profiles=dev

# 2. the harness, on 8081 — the origin the server's CORS allow-list names.
#    A file:// page sends Origin: null and will be refused.
python3 -m http.server 8081 --bind 127.0.0.1 --directory eval/harness
# open http://127.0.0.1:8081/interview.html
```

Enter a candidate ID and press Start. Questions arrive over SSE; record, check the transcript the
server heard, then submit.

## Why not MediaRecorder

The plan said MediaRecorder. Phase 0's audio probe showed why that is wrong here: MediaRecorder
produces WebM/Opus, and `GoogleSpeechController` decodes with `javax.sound`
(`AudioSystem.getAudioInputStream` plus an MP3 fallback), which cannot read WebM. So this records
**16 kHz mono 16-bit WAV** via the Web Audio API — matching `STT_FORMAT` exactly, since the decoder
does not resample.

## Why the transcript is editable

The candidate sees what the server actually heard before submitting. That is not a nicety: measured
word error rate on clean synthesised speech is **0.132**, and the errors land on technical terms —
`Redis` → "Reedus", `on write` → "on right", `filesort` → "fill a sword". Grading someone on a
mistranscription of their own answer is the failure this prevents, and it is why the field is a
textarea rather than a label.

For the **C3 measurement** the operator should submit the transcript *unedited*, so the number
reflects the real pipeline. For a session where the candidate's actual competence is what matters,
let them correct it. Record which mode was used.

## Blank transcripts are refused

A failed STT is not an answer. The server rejects a blank transcript rather than writing an empty
`ANSWER` turn — the grader cannot distinguish "said nothing" from "the microphone failed", and only
the former should ever be gradeable.

## Both C5 arms run through this page

The scripted arm is the degraded path (rung 9): same endpoints, same transport, same STT, same
timing, model disabled. Running the agent arm here and the scripted arm on the uni-app client would
compare two clients as much as two strategies.

## Data handling

`eval/audio/` is gitignored and audio never enters the repository. Transcripts are committed only
after names, employers and project names are stripped. The consent notice at the top of the page is
the minimum to read aloud before recording anyone.
