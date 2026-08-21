-- ---------------------------------------------------------------------------------------------
-- Where an agent interview's verdict goes.
--
-- The scripted pipeline writes an HTML blob into interview_record.result, which is fine for
-- rendering and useless for anything else: you cannot aggregate it, you cannot compare two
-- candidates on one dimension, and you cannot compute agreement against a human label without
-- parsing prose back out of markup.
--
-- The agent path already produces a structured Verdict - four dimensions, 1 to 5, each with the
-- evidence it was based on - because the grader uses constrained decoding. Storing it structurally
-- is what makes eval/labeling_sheet.csv joinable to real sessions later.
--
-- One verdict per session, enforced by the primary key rather than by convention: re-grading a
-- session replaces its verdict instead of accumulating a history nobody reads.
-- ---------------------------------------------------------------------------------------------

CREATE TABLE `interview_verdict` (
    `session_id`      VARCHAR(32)  NOT NULL,
    `candidate_id`    VARCHAR(32)  NOT NULL,
    `job_id`          VARCHAR(32)  NOT NULL,
    `overall`         INT          NOT NULL COMMENT '1 to 5. Enforced by the verdict schema at decode time, not by validation after the fact.',
    `recommendation`  VARCHAR(16)  NOT NULL COMMENT 'Derived in code from overall, never asked of the model - it agreed with its own score on 1 of 12 verdicts when asked.',
    `summary`         TEXT         NULL,
    `dimensions_json` TEXT         NOT NULL COMMENT 'The four dimension scores with the evidence each was based on. JSON because the evidence is what makes a score checkable, and it does not fit a column.',
    `graded_ms`       BIGINT       NOT NULL DEFAULT 0,
    `created_time`    DATETIME     NOT NULL,
    PRIMARY KEY (`session_id`),
    KEY `idx_verdict_candidate` (`candidate_id`),
    KEY `idx_verdict_job_overall` (`job_id`, `overall`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='One structured verdict per agent interview. Structured rather than HTML so scores can be aggregated and compared against human labels.';
