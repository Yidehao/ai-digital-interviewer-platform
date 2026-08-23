-- ---------------------------------------------------------------------------------------------
-- Anchored examples, against scale compression.
--
-- The cohort used 2 of the 5 points on offer. Designed-weak averaged 2.25, designed-mixed and
-- designed-strong BOTH averaged 3.00 - meaning the instrument could not separate a mixed candidate
-- from a strong one at all, which is the distinction a hiring decision actually turns on. Spearman
-- against designed rank was 0.71, so the ordering is broadly right and the RESOLUTION is not.
--
-- The previous rubric said "1 poor, 2 below bar, 3 meets bar, 4 above bar, 5 excellent" and "use
-- the whole range rather than clustering on 3". Stating the range did not produce the range - the
-- same lesson as `recommendation`, where naming the mapping in the schema changed nothing. Models
-- follow demonstrations more reliably than instructions.
--
-- So each dimension now carries concrete text for what a 2 and a 4 look like. If the spread does
-- not widen after this, the honest response is to stop claiming five points and report three bands.
-- That decision belongs to whoever reads the re-run, and it is a legitimate outcome rather than a
-- failure.
-- ---------------------------------------------------------------------------------------------

UPDATE `job`
SET `grader_prompt` = CONCAT(
    'Grade this candidate for the role using the four competencies below. Each is scored 1 to 5.\n',
    '\n',
    'Before scoring, you will list the technical claims the candidate made. Score correctness\n',
    'against that list, not against how the answer was worded.\n',
    '\n',
    '--- correctness: are the technical claims true?\n',
    '  A 2 looks like: "Redis is faster so I put it in front of the database." True but shallow,\n',
    '    and it contains one claim where a 4 contains several.\n',
    '  A 4 looks like: "I cached the read path with a 30 second TTL and invalidated on write,\n',
    '    because the stale window after an edit was what users actually complained about." Several\n',
    '    claims, all correct, and the mechanism is right.\n',
    '  A confident wrong answer scores below an uncertain right one. Judge the claims.\n',
    '\n',
    '--- depth: does it reach the trade-off?\n',
    '  A 2 looks like: naming the tool and stopping. "I would use a queue."\n',
    '  A 4 looks like: naming when the tool is WRONG, and why. "A queue decouples them, but it\n',
    '    turns a synchronous failure into a silent backlog, so it needs a dead-letter path and an\n',
    '    alarm on queue age or you have moved the problem rather than solved it."\n',
    '\n',
    '--- communication: could a teammate act on this answer?\n',
    '  A 2 looks like: a correct idea you have to reconstruct - jumps between topics, leaves the\n',
    '    conclusion implicit.\n',
    '  A 4 looks like: the same idea with its structure visible - what the problem was, what was\n',
    '    tried, what happened.\n',
    '  Judge STRUCTURE and CLARITY OF REASONING ONLY. Do NOT judge accent, grammar, idiom, article\n',
    '  use, or vocabulary range. A candidate explaining a correct idea in plain or non-idiomatic\n',
    '  English is communicating WELL. If you would score this differently had the same content been\n',
    '  said by a native speaker, your score is wrong.\n',
    '\n',
    '--- practical_experience: evidence of having actually done this?\n',
    '  A 2 looks like: textbook phrasing with no specifics, however fluent.\n',
    '  A 4 looks like: numbers, failures and constraints from a real system. "p99 went from 180ms\n',
    '    to 12ms, but we got a thundering herd on cold start and had to add a lock."\n',
    '  Claims about the candidate own history cannot be verified. Treat them as evidence of\n',
    '  experience, not as claims to be marked right or wrong.\n',
    '\n',
    'Scale: 1 poor, 2 below bar, 3 meets bar for the role, 4 above bar, 5 excellent.\n',
    'Use the full range. If every candidate you grade scores 3, the instrument is not working.\n',
    '\n',
    'LENGTH IS NOT A SCORE. A short precise answer and a long vague one covering the same content\n',
    'receive the same score. Words that add no claim add nothing. A candidate who answers in two\n',
    'sentences what another takes two minutes to say has not shown less depth.\n',
    '\n',
    'If a competency was never exercised by the questions asked, say so and score it\n',
    'conservatively rather than inferring it from the others.'
)
WHERE `grader_prompt` IS NULL
   OR `grader_prompt` = ''
   OR `grader_prompt` LIKE 'Grade this candidate for the role using the four competencies below.%';
