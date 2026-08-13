# eval/

Everything that measures the agent rather than running it.

## ⚠️ The golden set here is currently SYNTHETIC

`eval/golden/` and `eval/labels/` contain machine-generated sessions and machine-generated
labels, produced by `make_synthetic_golden.py`. Every file carries `"synthetic": true`, and
`metrics.py` prints a banner when it sees them.

They exist because the eval pipeline should not be blocked on ten people's calendars. They are
a **fixture for developing the harness**, and they are useful for exactly that: verifying the
metric implementations, settling the file format, exercising the grader end to end, and finding
crashes.

**They are not a benchmark.** The "two labelers" are one random number generator with two seeds,
so their agreement is a property of the script. Nothing from this data supports a claim
containing the words *human-labeled*, and no accuracy or human-ceiling number derived from it
is reportable.

Real sessions replace these files one for one — same schema, `synthetic` flips to `false`.
`eval/recruiting/` has everything needed to collect them.

---

## Layout

| Path | What it is |
|---|---|
| `bench_tools.py` | **A1** — does the model emit valid tool calls? Emission, argument validity, tool choice, and the repair rung measured rather than assumed |
| `bench_latency.py` | **A2** — prompt-eval throughput and first-token latency at 500/1500/3000 tokens |
| `situations.py` | 20 interviewer situations in the shape the real loop sends, including two prompt-injection cases |
| `tools/schemas.py` | The six tool schemas — `v1` as first measured, `v2` with the fixes applied |
| `report.py` | Renders raw results into `results-data.md`; run it rather than transcribing numbers |
| `metrics.py` | QWK, exact, ±1, MAE, bias, Spearman. Pure Python, no numpy |
| `question-bank.json` | 12 questions with reference answers — loadable into the real DB, and the source for the synthetic set |
| `answers.py` | Hand-written answer pool, three quality tiers per question, with speech texture |
| `make_synthetic_golden.py` | Generates the fixture described above |
| `harness/audio-probe.html` | Determines which audio formats `/speech/uploadVoice` accepts |
| `recruiting/` | Everything for collecting real sessions: messages, runbook, labeling guide, tracker |
| `tool-design-findings.md` | What A1 says about the schemas — inputs to Phase 1 |
| `model-selection.md` | The Phase 0.5 verdict |

---

## Running things

```bash
# A1 — the model gate
python3 eval/bench_tools.py --models qwen2.5:7b-instruct --repeat 3 --repair --schema-version v2

# A2 — latency on this machine
python3 eval/bench_latency.py

# regenerate the data appendix
python3 eval/report.py > eval/results-data.md

# agreement metrics over whatever is in eval/labels/
python3 eval/metrics.py

# rebuild the synthetic fixture (destructive — clears golden/ and labels/)
python3 eval/make_synthetic_golden.py --participants 10 --sessions-per-participant 2

# audio format probe
python3 -m http.server 8081 --directory eval/harness
# then open http://127.0.0.1:8081/audio-probe.html
```

Requires `jsonschema` and `requests`, plus Ollama on `:11434` for the benchmarks.

---

## Loading the question bank into the real database

The live bank has 2 questions, which is not enough for non-overlapping session subsets. Before
collecting real sessions, load `question-bank.json` — replacing `aiSrcPlaceholder` with a real
MinIO URL first. One avatar clip can serve all twelve; the question text is overlaid on screen,
so there is no need to record twelve videos.

## Data handling

`eval/audio/` is gitignored and audio never enters the repository. Transcripts are committed
only after names, employers, and project names are stripped. See `recruiting/README.md`.
