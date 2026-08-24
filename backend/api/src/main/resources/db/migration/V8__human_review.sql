-- ---------------------------------------------------------------------------------------------
-- The reviewer's own assessment, stored beside the model's rather than on top of it.
--
-- A console that shows a verdict and offers "approve" produces rubber-stamping. Automation bias is
-- the documented failure mode of decision-support tools: people agree with the machine, especially
-- when agreeing is one click and disagreeing means writing a justification. So the reviewer is
-- asked for their OWN scores, before the model's are revealed, and both are kept.
--
-- Two things fall out of that, and the second is the reason to do it this way:
--
--   1. The human judgement exists independently instead of as an endorsement of the model's.
--   2. Every reviewed interview becomes a labeled data point. The human ceiling currently rests on
--      twelve authored transcripts and two labelers; this grows it into one measured on real
--      interviews, as a by-product of work someone was doing anyway. That is the only path to a
--      ceiling that is not a pilot.
--
-- `decision` is the reviewer's, and there is no column for a model decision. The grader records
-- scores; it does not record outcomes.
-- ---------------------------------------------------------------------------------------------

CREATE TABLE `verdict_review` (
    `session_id`           VARCHAR(32)  NOT NULL,
    `reviewed_by`          VARCHAR(64)  NOT NULL,
    `overall`              INT          NULL COMMENT 'The reviewer''s own 1-5, given BEFORE the model''s scores were shown.',
    `correctness`          INT          NULL,
    `depth`                INT          NULL,
    `communication`        INT          NULL,
    `practical_experience` INT          NULL,
    `decision`             VARCHAR(32)  NOT NULL COMMENT 'advance / reject / needs_another_round. The reviewer''s call. There is deliberately no column for a model decision.',
    `notes`                TEXT         NULL,
    `created_time`         DATETIME     NOT NULL,
    PRIMARY KEY (`session_id`, `reviewed_by`),
    KEY `idx_review_decision` (`decision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Human assessments of interviews. Composite key on (session, reviewer) so a second reviewer is a second row rather than an overwrite - which is what makes inter-rater agreement on REAL interviews computable later.';
