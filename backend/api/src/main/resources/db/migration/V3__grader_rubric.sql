-- ---------------------------------------------------------------------------------------------
-- A grading rubric that the grader can actually follow.
--
-- `job.grader_prompt` was added in V2 and left NULL, so every agent-path grade fell back to
-- `job.prompt`. That column holds the SCRIPTED pipeline's prompt, and it instructs:
--
--     "Score each question out of 10 points"      -- the verdict schema is 1 to 5
--     "Use clear structured HTML ... <div> tags"  -- the verdict schema is JSON
--
-- Handing that to the agent grader is not a weak rubric, it is a contradictory one: the prompt
-- demands a scale and a format that constrained decoding makes unrepresentable. The model cannot
-- comply and cannot say so, so it silently does something else. That is worse than the generic
-- fallback string, because it looks like a configured rubric.
--
-- Backfilled only where NULL. A job that has had a rubric written for it keeps it.
--
-- This text is a STARTING POINT, not a validated instrument. It was written to match the four
-- dimensions the schema already fixes; nobody has checked that two humans reading it agree on what
-- a 3 means, which is what `eval/labeling_sheet.csv` exists to find out. Edit it per role.
-- ---------------------------------------------------------------------------------------------

UPDATE `job`
SET `grader_prompt` = CONCAT(
    'Grade this candidate for the role using the four competencies below. Each is scored 1 to 5.\n',
    '\n',
    'correctness - are the technical claims true? A confident wrong answer scores below an\n',
    'uncertain right one. Judge the claims, not the confidence.\n',
    '\n',
    'depth - does the answer go past the definition to the trade-off? Naming a tool is 2. Saying\n',
    'when it is the wrong tool, and why, is 4 or 5.\n',
    '\n',
    'communication - could a teammate act on this answer? Judge structure and clarity of\n',
    'reasoning. Do NOT judge accent, grammar, idiom, or vocabulary range. A candidate explaining a\n',
    'correct idea in plain or non-idiomatic English is communicating well.\n',
    '\n',
    'practical_experience - is there evidence of having actually done this? Specific numbers,\n',
    'failures, and constraints from real systems count. Textbook phrasing with no specifics does\n',
    'not, however fluent.\n',
    '\n',
    'Scale: 1 poor, 2 below bar, 3 meets bar for the role, 4 above bar, 5 excellent. 3 is a pass,\n',
    'not a failure - use the whole range rather than clustering on 3.\n',
    '\n',
    'Length is not a score. A short precise answer and a long vague one on the same content are\n',
    'the same score; verbosity that adds no claim adds nothing.\n',
    '\n',
    'If a competency was never exercised by the questions asked, say so and score it\n',
    'conservatively rather than inferring it from the others.'
)
WHERE `grader_prompt` IS NULL OR `grader_prompt` = '';
