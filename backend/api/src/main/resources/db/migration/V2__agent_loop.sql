-- Phase 3: persistence for the agent loop.
--
-- Additive only. Nothing here changes an existing table's meaning, and the fixed pipeline keeps
-- working untouched - which is what makes rung 9 of the fallback ladder a real safety net rather
-- than a story. `job.interview_mode` defaults to 'scripted', so this migration alone changes the
-- behaviour of exactly zero interviews.

-- ---------------------------------------------------------------- existing tables, extended

ALTER TABLE `job`
    ADD COLUMN `interview_mode` VARCHAR(16) NOT NULL DEFAULT 'scripted'
        COMMENT 'scripted = the fixed pipeline, agent = the model-driven loop. Per job, so one job can be flipped without touching the rest.',
    ADD COLUMN `grader_prompt` TEXT NULL
        COMMENT 'Rubric for the grader. Falls back to `prompt` when null - the interviewer and the grader want different instructions, but not every job has both written yet.';

-- ---------------------------------------------------------------- the session

CREATE TABLE `interview_session` (
    `id`               VARCHAR(32)  NOT NULL,
    `candidate_id`     VARCHAR(32)  NOT NULL,
    `job_id`           VARCHAR(32)  NOT NULL,
    `interviewer_id`   VARCHAR(32)  NOT NULL,
    `state`            VARCHAR(16)  NOT NULL COMMENT 'CREATED, RUNNING, FINISHED, DEGRADED, FAILED',
    `terminal_reason`  VARCHAR(32)  NULL COMMENT 'Set only when the loop ended the interview rather than the model. Null is the healthy case.',
    `closing_message`  VARCHAR(1024) NULL,
    `turn_count`       INT          NOT NULL DEFAULT 0,
    `tool_call_count`  INT          NOT NULL DEFAULT 0,
    `error_count`      INT          NOT NULL DEFAULT 0,
    `prompt_tokens`    INT          NOT NULL DEFAULT 0 COMMENT 'Summed prompt_eval_count. Prompt evaluation dominates first-token latency, so this is the number that says whether the sliding window is working.',
    `completion_tokens` INT         NOT NULL DEFAULT 0 COMMENT 'Summed eval_count.',
    `started_at`       DATETIME     NOT NULL,
    `finished_at`      DATETIME     NULL,
    `created_time`     DATETIME     NOT NULL,
    `updated_time`     DATETIME     NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_session_candidate` (`candidate_id`),
    KEY `idx_session_job_state` (`job_id`, `state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='One run of the agent loop. The hot copy lives in Redis; this is the durable record.';

-- ---------------------------------------------------------------- the transcript

CREATE TABLE `interview_turn` (
    `id`              VARCHAR(32)  NOT NULL,
    `session_id`      VARCHAR(32)  NOT NULL,
    `seq`             INT          NOT NULL,
    `kind`            VARCHAR(16)  NOT NULL COMMENT 'QUESTION, FOLLOWUP, ANSWER, CLOSING',
    `question_id`     VARCHAR(32)  NULL,
    `text`            TEXT         NOT NULL,
    `stt_confidence`  DOUBLE       NULL COMMENT 'ANSWER turns only. Captured but not yet acted on - Phase 4 either defines a policy or drops the column.',
    `started_at`      DATETIME     NULL,
    `ended_at`        DATETIME     NULL,
    `created_time`    DATETIME     NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_turn_session_seq` (`session_id`, `seq`),
    KEY `idx_turn_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='The transcript, and the grader''s only input. Deliberately holds nothing the interviewer thought - no working scores, no evidence, no tool names.';

-- ---------------------------------------------------------------- the tool log

CREATE TABLE `tool_invocation` (
    `id`             VARCHAR(32)  NOT NULL,
    `session_id`     VARCHAR(32)  NOT NULL,
    `seq`            INT          NOT NULL,
    `tool_name`      VARCHAR(32)  NOT NULL,
    `args_json`      TEXT         NULL COMMENT 'Full arguments for fetch_question, score_response and run_code - none of which carry candidate speech. Null for the two tools whose arguments quote the candidate.',
    `args_hash`      VARCHAR(64)  NOT NULL COMMENT 'Always present. For ask_followup.question and record_evidence.quote this is all that is stored, so a repeat is still detectable without persisting what the candidate said.',
    `outcome`        VARCHAR(16)  NOT NULL COMMENT 'OK, SCHEMA_REJECTED, ERROR, TIMEOUT',
    `fallback_reason` VARCHAR(32) NULL COMMENT 'Which rung fired, when one did.',
    `duration_ms`    BIGINT       NOT NULL DEFAULT 0,
    `created_time`   DATETIME     NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_tool_session_seq` (`session_id`, `seq`),
    KEY `idx_tool_name_outcome` (`tool_name`, `outcome`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Every tool call. This is the table the "adaptive" claim is checked against: run two candidates with different answers and diff their rows.';
