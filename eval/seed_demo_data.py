#!/usr/bin/env python3
"""
Get the dev database into the state the Phase 3 demo needs. Safe to re-run.

Two jobs:

  1. Retire the two legacy questions that duplicate loaded ones in substance.
     "Let's say something goes wrong in production..." and q-debug-incident are the same
     question with different wording, as are "How do you design a scalable system?" and
     q-scalable-system. The loader's text match does not catch a reword, so both survived, and
     fetch_question could serve a candidate both halves of a pair - a wasted slot that reads as
     the interviewer repeating itself. They are hidden (is_on=0), not deleted: the wording is the
     author's, and /questionLib/show puts either back.

  2. Create two demo candidates against the existing job.
     The Phase 3 demo is "run two candidates with different answers and diff the tool logs" -
     which needs two candidates. The candidate table is empty because rows are normally created
     by someone going through the app, and clicking through a signup form twice to get test data
     is not a good use of anyone's afternoon.

     identity_num and mobile are UNIQUE, so this checks before inserting and reports what it
     found rather than failing on a duplicate key.

    python3 eval/seed_demo_data.py --dry-run
    python3 eval/seed_demo_data.py
"""

import argparse
import json
import sys
import urllib.error
import urllib.request

DEFAULT_BASE = "http://127.0.0.1:8080"

# Deliberately unmistakable as test data, and inside the column limits:
# real_name varchar(16), identity_num varchar(18), mobile varchar(11).
DEMO_CANDIDATES = [
    {
        "realName": "Demo Alpha",
        "identityNum": "DEMOEVAL0000001",
        "mobile": "19000000001",
        "sex": 1,
        "email": "demo-alpha@example.invalid",
    },
    {
        "realName": "Demo Beta",
        "identityNum": "DEMOEVAL0000002",
        "mobile": "19000000002",
        "sex": 2,
        "email": "demo-beta@example.invalid",
    },
]

# The two originals, superseded by q-debug-incident and q-scalable-system.
LEGACY_QUESTION_MARKERS = [
    "Let’s say something goes wrong in production",
    "How do you design a scalable system?",
]

# urllib honours macOS system proxy settings and will route localhost through them; see
# load_question_bank.py for the failure this produces.
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def get_json(url):
    with OPENER.open(url, timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


def post_json(url, payload=None):
    data = json.dumps(payload).encode("utf-8") if payload is not None else b""
    request = urllib.request.Request(
        url, data=data, headers={"Content-Type": "application/json"}, method="POST")
    with OPENER.open(request, timeout=15) as response:
        return json.loads(response.read().decode("utf-8"))


def questions(base_url):
    body = get_json(f"{base_url}/questionLib/list?aiName=&question=&page=1&pageSize=500")
    return body["data"]["rows"]


def candidates(base_url):
    # Both realName and mobile are required params with no default; omitting either is a 555.
    body = get_json(f"{base_url}/candidate/list?realName=&mobile=&page=1&pageSize=500")
    return body["data"]["rows"]


def retire_duplicates(base_url, dry_run):
    print("retiring superseded questions")
    rows = questions(base_url)
    hidden = 0
    for marker in LEGACY_QUESTION_MARKERS:
        matches = [r for r in rows if marker in r["question"]]
        if not matches:
            print(f"  not found (already gone?)  {marker[:45]}...")
            continue
        for row in matches:
            if row.get("isOn") == 0:
                print(f"  already hidden             {row['question'][:45]}...")
                continue
            if dry_run:
                print(f"  would hide                 {row['question'][:45]}...")
            else:
                result = post_json(
                    f"{base_url}/questionLib/hide?questionLibId={row['questionLibId']}")
                if not result.get("success"):
                    print(f"  FAILED {row['questionLibId']}: {result}")
                    continue
                print(f"  hidden                     {row['question'][:45]}...")
            hidden += 1
    return hidden


def seed_candidates(base_url, job_id, dry_run):
    print("\nseeding demo candidates")
    existing = {c.get("identityNum") for c in candidates(base_url)}
    created = 0
    for candidate in DEMO_CANDIDATES:
        if candidate["identityNum"] in existing:
            print(f"  exists   {candidate['realName']}")
            continue
        payload = dict(candidate, jobId=job_id)
        if dry_run:
            print(f"  would create {candidate['realName']}")
        else:
            result = post_json(f"{base_url}/candidate/createOrUpdate", payload)
            if not result.get("success"):
                print(f"  FAILED {candidate['realName']}: {result}")
                continue
            print(f"  created  {candidate['realName']}")
        created += 1
    return created


def main():
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default=DEFAULT_BASE)
    parser.add_argument("--job-id", default=None,
                        help="defaults to the only job in the database")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    try:
        job_id = args.job_id
        if job_id is None:
            jobs = get_json(f"{args.base_url}/job/list?jobName=&page=1&pageSize=50")
            rows = jobs["data"]["rows"]
            if len(rows) != 1:
                raise SystemExit(
                    f"found {len(rows)} jobs; pass --job-id to say which one")
            job_id = rows[0].get("jobId") or rows[0].get("id")
        print(f"backend  {args.base_url}\njob      {job_id}\n")

        retire_duplicates(args.base_url, args.dry_run)
        seed_candidates(args.base_url, job_id, args.dry_run)

        if not args.dry_run:
            enabled = [q for q in questions(args.base_url) if q.get("isOn") == 1]
            print(f"\nbank now has {len(enabled)} enabled questions, "
                  f"{len(candidates(args.base_url))} candidates")
    except urllib.error.URLError as e:
        raise SystemExit(
            f"cannot reach {args.base_url}: {e}\n"
            "Start the backend:  cd backend && mvn -pl api spring-boot:run")
    return 0


if __name__ == "__main__":
    sys.exit(main())
