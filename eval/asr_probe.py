#!/usr/bin/env python3
"""
C3 - measure the ASR path, not just clean text.

The golden transcripts are clean prose. Production input is speech-to-text output. Anything the
grader disagrees with a human about might be the grader, or it might be the microphone, and without
this number there is no way to tell them apart.

WHAT THIS ACTUALLY MEASURES

  Real audio through the real endpoint. Each answer is synthesised with macOS `say`, POSTed to
  /speech/uploadVoice, and the returned transcript is compared to the text that was spoken. The
  word error rate is genuine: real encoding, real Google STT, real decode path.

WHAT IT DOES NOT MEASURE, AND THIS MATTERS MORE THAN THE NUMBER

  Synthesised speech is not human speech. It has no accent, no disfluency, no false starts, no
  background noise, no cross-talk, no microphone that is too far away, no candidate thinking out
  loud mid-sentence. Every one of those raises WER, and the second-language speakers this tool will
  be used on are exactly the population where ASR degrades most.

  So the number below is a FLOOR, not an estimate. It answers "does the pipeline mangle even clean
  input?" - a real and useful question with a real failure mode - and it does not answer "what WER
  will we see in production." Only recordings of actual people answer that.

  Reporting this as the project's WER would be dishonest. Report it as a pipeline floor.

    python3 eval/asr_probe.py            # needs the backend running
"""

import argparse
import json
import re
import subprocess
import sys
import tempfile
import urllib.request
import uuid
from pathlib import Path

HERE = Path(__file__).parent
DEFAULT_BASE = "http://127.0.0.1:8080"

# See load_question_bank.py: urllib honours macOS system proxy settings and will route localhost
# through them, producing a closed connection while curl to the same URL succeeds.
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def normalise(text):
    """Lowercase, strip punctuation, collapse whitespace. Standard for WER."""
    return " ".join(re.sub(r"[^a-z0-9\s]", " ", (text or "").lower()).split())


def word_error_rate(reference, hypothesis):
    """
    Levenshtein distance over words, divided by reference length.

    Substitutions, insertions and deletions all count once, which is the standard definition -
    "eight seconds" heard as "8 seconds" is one substitution, not a free pass.
    """
    ref = normalise(reference).split()
    hyp = normalise(hypothesis).split()
    if not ref:
        return None, 0, 0

    # Classic DP table. Small inputs, so the O(n*m) memory is irrelevant.
    d = [[0] * (len(hyp) + 1) for _ in range(len(ref) + 1)]
    for i in range(len(ref) + 1):
        d[i][0] = i
    for j in range(len(hyp) + 1):
        d[0][j] = j
    for i in range(1, len(ref) + 1):
        for j in range(1, len(hyp) + 1):
            cost = 0 if ref[i - 1] == hyp[j - 1] else 1
            d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
    return d[len(ref)][len(hyp)] / len(ref), d[len(ref)][len(hyp)], len(ref)


def synthesise(text, wav_path, voice, rate):
    """
    macOS `say` straight to 16 kHz mono LINEAR16 WAV.

    That format is not incidental: it is what Phase 0's audio probe established the endpoint
    accepts, and what the browser harness records.
    """
    subprocess.run(
        ["say", "-v", voice, "-r", str(rate), "--data-format=LEI16@16000",
         "-o", str(wav_path), text],
        check=True, capture_output=True)


def transcribe(base_url, wav_path):
    """POST the wav to the real endpoint and return whatever comes back."""
    boundary = uuid.uuid4().hex
    body = bytearray()
    body += f"--{boundary}\r\n".encode()
    body += b'Content-Disposition: form-data; name="file"; filename="answer.wav"\r\n'
    body += b"Content-Type: audio/wav\r\n\r\n"
    body += wav_path.read_bytes()
    body += f"\r\n--{boundary}--\r\n".encode()

    request = urllib.request.Request(
        f"{base_url}/speech/uploadVoice", data=bytes(body),
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}, method="POST")
    with OPENER.open(request, timeout=120) as response:
        payload = json.loads(response.read().decode("utf-8"))
    if not payload.get("success"):
        return None, payload
    return payload.get("data"), payload


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default=DEFAULT_BASE)
    parser.add_argument("--voice", default="Samantha")
    parser.add_argument("--rate", type=int, default=175, help="words per minute")
    parser.add_argument("--fixture", default=str(HERE / "surface_variants.json"))
    parser.add_argument("--variant", default="neutral",
                        help="which surface variant to speak")
    parser.add_argument("--out", default=str(HERE / "asr_results.json"))
    args = parser.parse_args()

    fixture = json.loads(Path(args.fixture).read_text())
    questions = fixture["questions"]

    print(f"voice {args.voice} at {args.rate} wpm -> {args.base_url}/speech/uploadVoice")
    print("NOTE: synthesised speech. The WER below is a pipeline floor, not a production estimate.\n")

    rows = []
    total_errors = total_words = 0
    with tempfile.TemporaryDirectory() as tmp:
        for question in questions:
            spoken = question["answers"][args.variant]
            wav = Path(tmp) / f"{question['id']}.wav"
            try:
                synthesise(spoken, wav, args.voice, args.rate)
            except subprocess.CalledProcessError as e:
                print(f"  {question['id']}: say failed - {e.stderr.decode()[:120]}")
                continue

            size_kb = wav.stat().st_size / 1024
            try:
                heard, raw = transcribe(args.base_url, wav)
            except Exception as e:
                print(f"  {question['id']}: upload failed - {e}")
                print("     Is the backend running? "
                      "cd backend && mvn -pl api spring-boot:run")
                return 1

            if not heard:
                print(f"  {question['id']}: no transcript returned - {raw}")
                continue

            wer, errors, words = word_error_rate(spoken, heard)
            total_errors += errors
            total_words += words
            rows.append({"id": question["id"], "spoken": spoken, "heard": heard,
                         "wer": wer, "errors": errors, "words": words,
                         "wavKb": round(size_kb, 1)})
            print(f"  {question['id']:<16} {size_kb:6.1f} KB  WER {wer:.3f}  "
                  f"({errors}/{words} words)")

    if not rows:
        print("\nnothing transcribed")
        return 1

    overall = total_errors / total_words if total_words else None
    print(f"\n  aggregate WER: {overall:.3f}  ({total_errors}/{total_words} words)")
    print("\n  What this is:    the pipeline does not mangle clean input.")
    print("  What it is not:  a production WER. Real speech has accent, disfluency, false")
    print("                   starts and noise, all of which raise this - and raise it most")
    print("                   for the second-language speakers this tool is used on.")

    print("\n  Word-level differences, first example:")
    first = rows[0]
    print(f"    spoken: {first['spoken'][:150]}")
    print(f"    heard:  {first['heard'][:150]}")

    Path(args.out).write_text(json.dumps({
        "method": "macOS say -> /speech/uploadVoice -> Google STT",
        "caveat": "Synthesised speech. This WER is a floor, not a production estimate.",
        "voice": args.voice, "rate": args.rate, "variant": args.variant,
        "aggregateWer": overall, "rows": rows}, indent=2))
    print(f"\n  written to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
