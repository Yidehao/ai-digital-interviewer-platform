#!/usr/bin/env python3
"""
Load eval/question-bank.json into the running backend's question library.

Why this exists: the live bank has 2 questions. That is enough to prove the loop runs and not
enough for anything after it. The Phase 3 demo is "run two candidates with different answers and
diff the tool logs" - with a two-question bank both candidates get the same interview and the
diff shows nothing. Real sessions have the same problem: ten people answering the same two
questions is a golden set with no variety in it.

Two things this script has to be careful about, neither of them obvious:

  1. POST /questionLib/createOrUpdate INSERTS whenever `id` is blank. It does not upsert on
     question text. Running it twice would silently double the bank, and a duplicated question
     means `fetch_question` can serve the same thing twice in one interview. So this script reads
     the existing bank first and skips anything already present, and is safe to re-run.

  2. `aiSrc` is the avatar clip URL and it is stored absolute. The two existing rows point at
     http://192.168.0.104:9010, a LAN address this machine no longer has - so those clips are
     already dead. Pick the base deliberately:
        --minio-base http://127.0.0.1:9010    desktop browser / eval harness (default)
        --minio-base http://<lan-ip>:9010     a phone on the same wifi

One clip serves all twelve questions: the question text is overlaid on screen, so there is no
need to record twelve videos.

    python3 eval/load_question_bank.py --dry-run
    python3 eval/load_question_bank.py
"""

import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).parent
DEFAULT_BASE = "http://127.0.0.1:8080"
DEFAULT_MINIO = "http://127.0.0.1:9010"

# One existing clip in the `interviewer` bucket, reused for every question.
DEFAULT_CLIP = "/interviewer/d163e037-d8d1-4c4d-ad1e-9e2d3ce7ec3e.mp4"


# An empty ProxyHandler, deliberately. urllib reads macOS's system proxy settings, so on a machine
# with a proxy configured it will route a call to 127.0.0.1 through it and get a closed connection
# - while curl to the same URL succeeds, which makes it look like the script is broken rather than
# the transport. These calls are all localhost; nothing here should ever traverse a proxy.
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def get_json(url):
    with OPENER.open(url, timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


def post_json(url, payload):
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with OPENER.open(request, timeout=15) as response:
        return json.loads(response.read().decode("utf-8"))


def existing_bank(base_url):
    """Every question already in the library, as {normalised text: row}."""
    url = f"{base_url}/questionLib/list?aiName=&question=&page=1&pageSize=500"
    body = get_json(url)
    if not body.get("success"):
        raise SystemExit(f"list failed: {body}")
    rows = body["data"]["rows"]
    return {normalise(row["question"]): row for row in rows}


def normalise(text):
    """Curly quotes and whitespace differ between the JSON and the DB; the question does not."""
    return " ".join(
        text.replace("’", "'").replace("“", '"').replace("”", '"').split()
    ).lower()


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default=DEFAULT_BASE,
                        help=f"running backend (default {DEFAULT_BASE})")
    parser.add_argument("--minio-base", default=DEFAULT_MINIO,
                        help=f"MinIO origin for aiSrc (default {DEFAULT_MINIO}); "
                             "use the LAN IP if a phone will play the clips")
    parser.add_argument("--clip", default=DEFAULT_CLIP,
                        help="object path of the avatar clip, reused for every question")
    parser.add_argument("--interviewer-id", default=None,
                        help="defaults to the interviewer already owning the live bank")
    parser.add_argument("--bank", default=str(HERE / "question-bank.json"))
    parser.add_argument("--dry-run", action="store_true",
                        help="show what would be sent and change nothing")
    args = parser.parse_args()

    bank = json.loads(Path(args.bank).read_text())
    questions = bank if isinstance(bank, list) else bank.get("questions", bank)

    try:
        present = existing_bank(args.base_url)
    except urllib.error.URLError as e:
        raise SystemExit(
            f"cannot reach {args.base_url}: {e}\n"
            "Start the backend first:  cd backend && mvn -pl api spring-boot:run")

    interviewer_id = args.interviewer_id
    if interviewer_id is None:
        owners = {row.get("interviewerId") for row in present.values() if row.get("interviewerId")}
        if len(owners) != 1:
            raise SystemExit(
                "could not infer the interviewer id from the existing bank "
                f"(found {owners or 'none'}); pass --interviewer-id explicitly")
        interviewer_id = owners.pop()

    ai_src = args.minio_base.rstrip("/") + args.clip
    print(f"backend        {args.base_url}")
    print(f"interviewer    {interviewer_id}")
    print(f"aiSrc          {ai_src}")
    print(f"already loaded {len(present)} question(s)\n")

    created = skipped = 0
    for question in questions:
        text = question["question"]
        if normalise(text) in present:
            print(f"  skip    {question['key']}")
            skipped += 1
            continue

        payload = {
            "question": text,
            "referenceAnswer": question["referenceAnswer"],
            "aiSrc": ai_src,
            "interviewerId": interviewer_id,
            "isOn": 1,
        }
        if args.dry_run:
            print(f"  would create  {question['key']:<24} {text[:60]}...")
        else:
            result = post_json(f"{args.base_url}/questionLib/createOrUpdate", payload)
            if not result.get("success"):
                print(f"  FAILED  {question['key']}: {result}")
                continue
            print(f"  created {question['key']:<24} {text[:60]}...")
        created += 1

    print(f"\n{'would create' if args.dry_run else 'created'} {created}, skipped {skipped}")
    if not args.dry_run:
        total = len(existing_bank(args.base_url))
        print(f"bank now holds {total} questions")
    return 0


if __name__ == "__main__":
    sys.exit(main())
