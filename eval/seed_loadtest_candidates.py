#!/usr/bin/env python3
"""
Create (or delete) N throwaway candidates for the load test.

WHY THIS EXISTS

  The orchestrator allows one in-flight interview per candidate - `claimCandidate` is a Redis
  SETNX, and a double-tap on "start" must not produce two interviews for one person. That is
  correct behaviour and it makes the load test measure the wrong thing if you forget it: driving
  128 concurrent sessions against 2 candidate rows produces 2 sessions and 126 refusals, which
  looks exactly like a capacity ceiling and is not one.

  So the concurrency being measured is bounded by the number of candidate rows. One row per
  concurrent session, created up front and deleted afterwards.

    python3 eval/seed_loadtest_candidates.py --count 200
    python3 eval/seed_loadtest_candidates.py --delete
"""

import argparse
import json
import sys
import urllib.request

DEFAULT_BASE = "http://127.0.0.1:8080"
PREFIX = "LT"                      # real_name is varchar(16), so keep the marker short
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def get_json(url):
    with OPENER.open(url, timeout=30) as response:
        return json.loads(response.read().decode())


def post_json(url, payload):
    request = urllib.request.Request(
        url, data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"}, method="POST")
    with OPENER.open(request, timeout=30) as response:
        return json.loads(response.read().decode())


def existing(base_url):
    body = get_json(f"{base_url}/candidate/list?realName=&mobile=&page=1&pageSize=1000")
    return (body.get("data") or {}).get("rows") or []


def job_id(base_url):
    jobs = (get_json(f"{base_url}/job/list?jobName=&page=1&pageSize=10")
            .get("data") or {}).get("rows") or []
    if not jobs:
        print("no job exists - create one in the admin panel first")
        sys.exit(1)
    return jobs[0]["jobId"]


def seed(base_url, count):
    already = {c.get("identityNum") for c in existing(base_url)}
    jid = job_id(base_url)
    made = 0
    for i in range(count):
        identity = f"LOADTEST{i:010d}"
        if identity in already:
            continue
        payload = {
            "realName": f"{PREFIX}{i:05d}",
            "identityNum": identity,
            # 11 digits, and far outside anything a real person would hold.
            "mobile": f"199{i:08d}",
            "sex": 1,
            "email": f"loadtest-{i}@example.invalid",
            "jobId": jid,
        }
        result = post_json(f"{base_url}/candidate/createOrUpdate", payload)
        if result.get("status") != 200:
            print(f"  FAILED at {i}: {result}")
            break
        made += 1
    print(f"created {made} load-test candidates (job {jid})")


def delete(base_url):
    removed = 0
    for candidate in existing(base_url):
        if str(candidate.get("identityNum", "")).startswith("LOADTEST"):
            get_json(f"{base_url}/candidate/delete?candidateId={candidate['candidateId']}")
            removed += 1
    print(f"deleted {removed} load-test candidates")


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default=DEFAULT_BASE)
    parser.add_argument("--count", type=int, default=200)
    parser.add_argument("--delete", action="store_true")
    args = parser.parse_args()
    delete(args.base_url) if args.delete else seed(args.base_url, args.count)
    return 0


if __name__ == "__main__":
    sys.exit(main())
