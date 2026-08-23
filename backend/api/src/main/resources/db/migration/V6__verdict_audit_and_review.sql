-- ---------------------------------------------------------------------------------------------
-- Three things a verdict needs before it is allowed anywhere near a hiring decision.
--
-- 1. RECONSTRUCTABILITY. A verdict is currently a set of numbers with no record of what produced
--    them. If a candidate asks why they were scored a 2 - or a regulator does - the honest answer
--    today is "the model said so, and we no longer know which model, which rubric, or which
--    prompt". Every column below exists so a verdict can be rebuilt and re-run months later.
--
--    Hashes rather than full copies for the rubric and prompt: the text lives in `job` and in the
--    source tree, and a hash is enough to prove which version was in force while keeping this table
--    small enough to index.
--
-- 2. STABILITY. `samples` and the two spreads record that the transcript was graded more than once
--    and by how much the runs disagreed. Without them, "3 of 12 candidates moved between identical
--    runs" is a fact about a past experiment rather than a property being monitored.
--
-- 3. HUMAN REVIEW. `needs_human_review` and `reviewed_by` make review a state the row is in, not a
--    process someone remembers to follow. `advisory` is NOT NULL DEFAULT 1 with no code path that
--    sets it to 0: the measured surface-phrasing penalty scales with competence - worst for the
--    strongest non-native candidates, which is the worst possible shape near a cutoff - so nothing
--    here may reject anyone on its own.
-- ---------------------------------------------------------------------------------------------

ALTER TABLE `interview_verdict`
    ADD COLUMN `claims_json`        TEXT        NULL COMMENT 'Technical claims extracted before scoring, each correct/incorrect/unverifiable. Separating claims from prose is the lever against the correctness penalty measured on non-native phrasing.',
    ADD COLUMN `samples`            INT         NOT NULL DEFAULT 1 COMMENT 'How many times the transcript was graded before taking a median.',
    ADD COLUMN `dimension_spread`   INT         NOT NULL DEFAULT 0 COMMENT 'Largest disagreement between samples on any one dimension. 0 = every run agreed.',
    ADD COLUMN `overall_spread`     INT         NOT NULL DEFAULT 0 COMMENT 'The same, for the overall score.',
    ADD COLUMN `needs_human_review` TINYINT(1)  NOT NULL DEFAULT 1 COMMENT 'Set when samples disagreed. Defaults to 1 so a row that predates this column is not silently treated as reviewed.',
    ADD COLUMN `review_reason`      VARCHAR(500) NULL,
    ADD COLUMN `reviewed_by`        VARCHAR(64) NULL COMMENT 'Who signed off. NULL means nobody has.',
    ADD COLUMN `reviewed_time`      DATETIME    NULL,
    ADD COLUMN `advisory`           TINYINT(1)  NOT NULL DEFAULT 1 COMMENT 'Always 1. No code path sets it to 0. This is a screening aid, not an automated decision.',
    ADD COLUMN `model`              VARCHAR(64) NULL COMMENT 'Which model produced it.',
    ADD COLUMN `rubric_hash`        VARCHAR(64) NULL COMMENT 'SHA-256 of job.grader_prompt as it stood at grading time.',
    ADD COLUMN `prompt_hash`        VARCHAR(64) NULL COMMENT 'SHA-256 of the full rendered grader prompt, transcript included.',
    ADD COLUMN `schema_version`     VARCHAR(64) NULL COMMENT 'SHA-256 of verdict.json. Field ORDER is load-bearing here, so a reordering is a different instrument and must be visible as one.',
    ADD KEY `idx_verdict_review` (`needs_human_review`, `reviewed_by`);

-- ---------------------------------------------------------------------------------------------
-- The transcript ceiling.
--
-- interview_record.answer_content was VARCHAR(6000). A scripted interview of three questions fits;
-- an agent interview with follow-ups does not - the sessions run here reached 11 to 15 answers.
-- MySQL in non-strict mode TRUNCATES silently, which on a hiring record means the reviewer reads a
-- transcript that stops mid-sentence and has no way to know it did.
-- ---------------------------------------------------------------------------------------------

ALTER TABLE `interview_record`
    MODIFY COLUMN `answer_content` MEDIUMTEXT NOT NULL COMMENT 'Full transcript. Was VARCHAR(6000), which silently truncated agent interviews with follow-ups.';
