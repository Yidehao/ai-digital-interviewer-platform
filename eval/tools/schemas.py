"""
The six tool schemas, in the shape Ollama's /api/chat `tools` parameter expects.

These are the Phase 0.5 benchmark's copy. Phase 1 moves the canonical versions to
service/src/main/resources/tools/*.json; keep the two in sync until then.

Two conventions, both load-bearing:
  - every args schema sets additionalProperties=False, so a hallucinated field becomes a
    correctable validation error rather than a silently ignored argument
  - `required` lists only what the tool genuinely cannot run without
"""

DIMENSIONS = ["correctness", "depth", "communication", "practical_experience"]

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "fetch_question",
            "description": (
                "Fetch the next scripted question from this job's question bank. "
                "Use when you are ready to move on to a new topic."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "topic": {"type": "string", "maxLength": 64,
                              "description": "Optional topic hint."},
                    "difficulty": {"type": "string", "enum": ["easy", "medium", "hard"]},
                    "excludeServed": {"type": "boolean", "default": True,
                                      "description": "Skip questions already asked."},
                },
                "required": [],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "ask_followup",
            "description": (
                "Ask a follow-up question about the answer the candidate just gave. "
                "Use to probe a vague, shallow, or unusually interesting answer."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "question": {"type": "string", "maxLength": 400},
                    "rationale": {"type": "string", "maxLength": 300,
                                  "description": "Why this probe, in one sentence."},
                    "parentQuestionId": {"type": "string"},
                    "competency": {"type": "string", "enum": DIMENSIONS},
                },
                "required": ["question", "rationale", "parentQuestionId", "competency"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "score_response",
            "description": (
                "Record a private working score for one competency. This steers your own "
                "planning; it is not the final grade and the candidate never sees it."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "questionId": {"type": "string"},
                    "dimension": {"type": "string", "enum": DIMENSIONS},
                    "score": {"type": "integer", "minimum": 1, "maximum": 5},
                    "evidence": {"type": "string", "maxLength": 400},
                    "confidence": {"type": "string", "enum": ["low", "medium", "high"]},
                },
                "required": ["questionId", "dimension", "score", "evidence", "confidence"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "run_code",
            "description": (
                "Execute a code snippet the candidate dictated, in a sandbox, and return its "
                "output. Use when the candidate has described concrete code worth running."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "language": {"type": "string", "enum": ["python", "java", "javascript", "sql"]},
                    "source": {"type": "string", "maxLength": 8000},
                    "stdin": {"type": "string", "maxLength": 2000, "default": ""},
                    "timeoutMs": {"type": "integer", "minimum": 100, "maximum": 5000, "default": 3000},
                },
                "required": ["language", "source"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "record_evidence",
            "description": (
                "Attach a short quote from the candidate's answer to a competency, building an "
                "auditable trail. The quote must come from what the candidate actually said."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "competency": {"type": "string", "enum": DIMENSIONS},
                    "quote": {"type": "string", "maxLength": 500},
                    "judgment": {"type": "string", "enum": ["positive", "negative", "neutral"]},
                    "questionId": {"type": "string"},
                },
                "required": ["competency", "quote", "judgment", "questionId"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "finish_interview",
            "description": (
                "End the interview. Call this when coverage is sufficient, the bank is "
                "exhausted, or the candidate asks to stop. Ending is an explicit action."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "reason": {"type": "string",
                               "enum": ["complete", "questions_exhausted", "candidate_request",
                                        "budget", "error_budget", "degraded"]},
                    "closingMessage": {"type": "string", "maxLength": 600},
                },
                "required": ["reason", "closingMessage"],
                "additionalProperties": False,
            },
        },
    },
]

ARG_SCHEMAS = {t["function"]["name"]: t["function"]["parameters"] for t in TOOLS}


# ---------------------------------------------------------------------------
# v2 - the schemas revised in response to the v1 benchmark run.
#
# Every change below is a response to a specific observed failure, documented in
# eval/tool-design-findings.md. Keeping v1 intact means the improvement is measurable
# (`--schema-version v1` vs `v2`) rather than asserted.
# ---------------------------------------------------------------------------

def _v2():
    import copy
    tools = copy.deepcopy(TOOLS)
    by_name = {t["function"]["name"]: t["function"] for t in tools}

    # score_response: 12 calls used a score outside 1-5, and 17 omitted `confidence`.
    # State the range in the prose the model actually reads, and stop requiring a field
    # that steers nothing on its own.
    sr = by_name["score_response"]
    sr["description"] = (
        "Record a private working score for one competency, on a scale of 1 to 5 where "
        "1 is poor and 5 is excellent. This steers your own planning; it is not the "
        "final grade and the candidate never sees it."
    )
    sr["parameters"]["properties"]["score"]["description"] = (
        "Integer from 1 (poor) to 5 (excellent). Must not exceed 5."
    )
    sr["parameters"]["properties"]["confidence"]["default"] = "medium"
    sr["parameters"]["required"] = ["questionId", "dimension", "score", "evidence"]

    # ask_followup: four required fields on the most-reached-for tool cost a repair turn
    # on nearly every call. Require only what the tool cannot execute without.
    af = by_name["ask_followup"]
    af["parameters"]["required"] = ["question", "parentQuestionId"]
    af["parameters"]["properties"]["parentQuestionId"]["description"] = (
        "The questionId returned by the fetch_question call this follows up on."
    )

    # run_code: llama chose this in six situations containing no code at all. A negative
    # precondition does more work here than a positive description.
    rc = by_name["run_code"]
    rc["description"] = (
        "Execute a code snippet the candidate dictated, in a sandbox, and return its output. "
        "Only call this when the candidate has dictated actual, complete source code. "
        "Do NOT call it for a verbal description of an approach, for an answer containing no "
        "code, or when you simply want to assess the candidate."
    )

    # finish_interview: the model invented `time_exhausted`, which is a clearer name than
    # the schema's `budget`. Accept both.
    fi = by_name["finish_interview"]
    fi["parameters"]["properties"]["reason"]["enum"] = [
        "complete", "questions_exhausted", "candidate_request",
        "time_exhausted", "budget", "error_budget", "degraded",
    ]
    return tools


TOOLS_V2 = _v2()
ARG_SCHEMAS_V2 = {t["function"]["name"]: t["function"]["parameters"] for t in TOOLS_V2}

VERSIONS = {
    "v1": (TOOLS, ARG_SCHEMAS),
    "v2": (TOOLS_V2, ARG_SCHEMAS_V2),
}

SYSTEM_PROMPT = """You are conducting a technical job interview for a Software Engineer role.

You drive the interview by calling tools. Every turn, you must call exactly one tool.
Do not write prose to the user - the only way to say anything to the candidate is through
a tool call.

Available moves:
- fetch_question   - move on to a new question from the bank
- ask_followup     - probe the answer just given
- score_response   - record a private working score
- record_evidence  - quote the candidate against a competency
- run_code         - run code the candidate described
- finish_interview - end the interview

Cover four competencies: correctness, depth, communication, practical_experience.
Aim for roughly 5 questions. Ending the interview is an explicit action: call
finish_interview rather than simply stopping.
"""
