"""
Answer pool for the synthetic golden set: three quality tiers per question.

Hand-written rather than generated, because the texture is the point. Real spoken answers have
false starts, self-correction, filler, and thoughts that trail off — and a grader benchmarked
only on clean prose learns nothing about the input it will actually receive.

Tiers map to what a rubric would score roughly 4-5 (strong), 3 (mixed), 1-2 (weak). They are
deliberately not caricatures: the weak answers are plausible things a nervous junior candidate
says, not nonsense, because a benchmark where every weak answer is obviously weak measures
nothing — every grader agrees on those.
"""

# question key -> {tier: answer text}
ANSWERS = {
    "q-debug-incident": {
        "strong": (
            "First thing is figure out if it's real and how bad. I look at the error rate and "
            "the dashboards, because half the time an alert fires and it's one bad host. Then "
            "what changed — deploys, config, feature flags, in that order, because it's almost "
            "always something we did. If a deploy lines up I roll back before I understand it. "
            "That took me a while to learn honestly, I used to want to know why first, and "
            "meanwhile customers are down. Root cause after. And then a postmortem and usually "
            "an alert for whatever gap let it get that far."
        ),
        "mixed": (
            "I'd check the logs and the monitoring to see what's failing. Look at whether "
            "anything got deployed recently. If it's a bad deploy then roll it back, otherwise "
            "try to find where the error's coming from and fix it. And then afterwards, um, "
            "write up what happened so it doesn't happen again."
        ),
        "weak": (
            "I'd look at the logs to find the error, and then debug it and fix the bug. Maybe "
            "ask a senior engineer if I couldn't figure it out."
        ),
    },
    "q-scalable-system": {
        "strong": (
            "I'd want to know what scaling means here first — is it read heavy or write heavy, "
            "what's the traffic shape, does it need to be strongly consistent. Those give really "
            "different systems. Generally keep the services stateless so you can just add more, "
            "push state into a datastore or a cache. Caching I think about in terms of how stale "
            "is acceptable — catalogue data, a minute is fine; anything about money, no. "
            "Partitioning, I'd want to know the access pattern before picking a key, because "
            "picking wrong is painful to undo. And observability from day one, otherwise you're "
            "scaling something you can't see."
        ),
        "mixed": (
            "I'd start by estimating the load, like how many requests per second we're expecting "
            "and what the read write ratio is. Then make the services stateless so we can scale "
            "horizontally, add caching for the hot reads, and shard the database if it gets big "
            "enough. Probably a load balancer in front."
        ),
        "weak": (
            "I would use microservices so each part can scale on its own, and add caching and a "
            "load balancer. And make sure the database can handle the traffic, maybe use a "
            "NoSQL database since those scale better."
        ),
    },
    "q-code-review": {
        "strong": (
            "I wouldn't do it in PR comments, that's the main thing. A line comment saying the "
            "whole design is wrong is just bad — it's public and it's hard to respond to. I'd "
            "message them, ask what constraints they were working with, because pretty often "
            "there's a reason I don't know about. If I still think it's wrong after that, I'd "
            "say so directly with what I'd do instead. And it matters how far along they are. "
            "If it's two days of work, that's a real conversation about cost, not just "
            "correctness. I've been the person who was wrong in that conversation before."
        ),
        "mixed": (
            "I'd leave a comment explaining what I think the issue is and suggest a different "
            "approach. Try to be constructive about it rather than just saying it's wrong. If we "
            "still disagreed I'd probably ask someone else on the team what they think, or bring "
            "it up in standup."
        ),
        "weak": (
            "I'd explain in the comments why I think the approach has problems and ask them to "
            "change it. If they push back I'd probably just approve it, it's not worth arguing "
            "over and it's their code."
        ),
    },
    "q-db-index": {
        "strong": (
            "I'd run EXPLAIN first, before touching anything. Half the time what you assume is "
            "slow isn't the slow part. The other question is what changed — did the table grow, "
            "did someone add a column, did the query change, or did the statistics go stale so "
            "the planner picked a different path. Missing index is the obvious answer but I'd "
            "want to confirm the planner actually isn't using one. And I'd check whether it's "
            "slow for everyone or just one customer whose data got big, because that's a "
            "different problem. Adding an index isn't free either, it costs you on writes."
        ),
        "mixed": (
            "I'd look at the query plan to see what it's doing, check if there's an index on the "
            "columns in the where clause. If not I'd add one and see if it helps. Also check if "
            "the table has grown a lot recently."
        ),
        "weak": (
            "I'd add an index on the columns being queried, that usually fixes slow queries. Or "
            "add caching so it doesn't have to hit the database every time."
        ),
    },
    "q-api-design": {
        "strong": (
            "The contract is the part you can't take back, so that gets the most thought. "
            "Naming, and modelling the resources so they still make sense in a year. Anything "
            "that returns a list needs pagination from the start — retrofitting that breaks "
            "everyone. Errors matter more than people give them credit for: for each one the "
            "caller has to know whether to retry, fix their request, or give up, and if your "
            "errors don't tell them that they'll retry everything. Idempotency on writes so a "
            "network timeout doesn't create two orders. And how you'll deprecate it, because you "
            "will."
        ),
        "mixed": (
            "Making sure the endpoints are RESTful and the naming is consistent. Proper status "
            "codes, good error messages. Authentication and authorisation. Versioning so you can "
            "change it later without breaking clients. And documentation so people know how to "
            "use it."
        ),
        "weak": (
            "Use the right HTTP methods, GET for reading and POST for creating, and return the "
            "right status codes. Return JSON. And make sure it's secure with authentication."
        ),
    },
    "q-testing": {
        "strong": (
            "I think about what breaks quietly. A crash you find immediately; something that "
            "silently computes the wrong number for three weeks is the expensive one, so that "
            "gets tested. Also what changes a lot — code nobody touches doesn't need much. I'm "
            "sceptical of coverage as a target, you can hit ninety percent and test nothing that "
            "matters. There was a payments thing where I didn't write tests for the UI layer at "
            "all because it was changing weekly, and put everything into the calculation logic. "
            "That was the right call, the UI got rewritten twice."
        ),
        "mixed": (
            "I'd prioritise the critical paths, the things users actually do most. Unit tests for "
            "the business logic and integration tests for the main flows. Try to cover edge cases "
            "where things could break. Probably not worth testing simple getters and setters."
        ),
        "weak": (
            "I'd try to get good code coverage, aim for like eighty percent. Write unit tests for "
            "the functions and integration tests for the API endpoints."
        ),
    },
    "q-concurrency": {
        "strong": (
            "Classic lost update — both read the same value, both write, one silently wins. What "
            "I'd do depends on how bad losing one actually is. If it's a counter, an atomic "
            "increment or a conditional update in the database is enough. If it's something where "
            "the second person needs to know they were working from stale data, optimistic "
            "locking with a version column, so the second write fails and you can tell them. "
            "Pessimistic locks I'd avoid unless contention is genuinely high, they cost you more "
            "than people expect."
        ),
        "mixed": (
            "You could get a race condition where one update overwrites the other. I'd use a "
            "transaction so they don't interfere, or add a lock on the row so the second one has "
            "to wait. Optimistic locking with a version number would also work."
        ),
        "weak": (
            "There would be a race condition. I'd use a lock so only one request can update at a "
            "time, or put them in a queue so they get processed one after another."
        ),
    },
    "q-explain-technical": {
        "strong": (
            "I'd probably explain the caching layer we built. The way I put it to our PM was, "
            "imagine every time someone asks you a question you walk to a filing cabinet in "
            "another building. The cache is a notepad on your desk with the twenty things people "
            "ask about most. It's much faster, but the risk is the filing cabinet changes and "
            "your notepad is out of date — so the real question is how long you're willing to be "
            "wrong. She got it immediately and then asked how long we'd chosen, which was exactly "
            "the right question."
        ),
        "mixed": (
            "I'd use an analogy. Like if I was explaining an API I'd say it's like a waiter in a "
            "restaurant, you tell them what you want and they go to the kitchen and bring it back, "
            "you don't need to know how the kitchen works. Try to avoid technical terms and check "
            "if they're following."
        ),
        "weak": (
            "I would avoid using technical jargon and explain it in simple terms, using an analogy "
            "if that helps. And keep it high level rather than going into the details."
        ),
    },
    "q-tradeoff": {
        "strong": (
            "We had to pick between building notifications ourselves or using a hosted service. "
            "Hosted was faster to ship and someone else handles deliverability, which is genuinely "
            "hard. Building it meant control and no per-message cost, which mattered at our "
            "projected volume. We went hosted, mostly because we had six weeks and two engineers. "
            "The cost was real though — we're locked into their template system and it's awkward, "
            "and I'd guess we'll migrate within two years. I'd make the same call again with those "
            "constraints, but I'd have pushed harder to keep the template rendering on our side."
        ),
        "mixed": (
            "We were deciding between MongoDB and Postgres for a project. Mongo was more flexible "
            "with the schema which seemed good early on since requirements kept changing, but "
            "Postgres had better support for the relational queries we needed. We went with "
            "Postgres and it worked out well."
        ),
        "weak": (
            "We had to choose between two frameworks and I researched both and made a comparison, "
            "and we picked the one that was better for our use case and had more community "
            "support."
        ),
    },
    "q-debug-hard-bug": {
        "strong": (
            "Intermittent failure, maybe one request in two thousand, only in production. Took "
            "about a week. The hard part was I couldn't reproduce it, so I was guessing, and my "
            "first hypothesis was completely wrong — I was sure it was a race in our code and "
            "spent two days there. What actually helped was adding enough logging to catch it in "
            "the act. Turned out to be a connection pool sized smaller than the thread pool, so "
            "under a burst some threads got a timeout that we were swallowing and turning into a "
            "generic error. Fixed the sizing, and stopped swallowing that exception, which was "
            "the real bug."
        ),
        "mixed": (
            "There was a bug where data was occasionally coming out wrong and it was hard to "
            "reproduce. I added a lot of logging to narrow down where it was happening, and "
            "eventually found it was a timezone issue where we were converting dates in two "
            "different places. Took a few days to find."
        ),
        "weak": (
            "I had a bug once that was really hard because the error message wasn't helpful. I "
            "used the debugger and stepped through the code until I found where it was going "
            "wrong, and then fixed it."
        ),
    },
    "q-legacy": {
        "strong": (
            "Before changing anything I'd write tests that pin down what it currently does — not "
            "what it should do, what it does, bugs included. That gives you a safety net without "
            "needing to understand it all first. I'd also try to find out why it's like that. Old "
            "code that's been in production for years usually has weird branches because "
            "something weird actually happened once. Then small steps, each one deployable and "
            "reversible. And I'd watch it in production after, because with code like that the "
            "test suite you just wrote is not covering everything."
        ),
        "mixed": (
            "I'd write tests first so I know if I break something, then make the change "
            "incrementally rather than all at once. Try to understand what the code is doing "
            "before changing it, and get someone who knows the system to review it."
        ),
        "weak": (
            "I'd read through the code carefully to understand it, then make the change and test "
            "it manually to make sure it still works. Maybe refactor it while I'm in there since "
            "it's probably messy."
        ),
    },
    "q-disagreement": {
        "strong": (
            "We decided to build our own auth instead of using a provider. I thought it was a bad "
            "use of time and said so — wrote up the reasoning, what it'd cost us, what we'd give "
            "up. The lead's argument was a compliance requirement I genuinely hadn't known about. "
            "So I was missing information, which is worth noticing, because I'd been fairly "
            "confident. We built it. I still think we over-built it, but the decision to build was "
            "right and I'd have argued us into a worse place. I try to make the case once, "
            "properly, and then commit."
        ),
        "mixed": (
            "The team wanted to use a technology I didn't think was the right fit. I raised my "
            "concerns in the meeting and explained why I thought it would cause problems later. "
            "The team went ahead with it anyway, and I went along with the decision since it was "
            "the team's call. It ended up being okay."
        ),
        "weak": (
            "I disagreed with a decision once but I brought it up and we talked about it, and in "
            "the end I went with what the team wanted because it's important to be a team player."
        ),
    },
}

# Speech-to-text artefacts applied to a fraction of answers, so the golden set exercises the
# text the grader will actually see rather than clean prose.
ASR_SUBSTITUTIONS = [
    ("idempotency", "item potency"),
    ("idempotent", "item potent"),
    ("Postgres", "post grass"),
    ("EXPLAIN", "explain"),
    ("deliverability", "deliver ability"),
    ("stateless", "state less"),
    ("optimistic locking", "optimistic blocking"),
]

DISFLUENCIES = ["um, ", "like, ", "I mean, ", "you know, ", "so, "]
