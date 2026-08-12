-- ---------------------------------------------------------------------------
-- Baseline: the schema the application ran on before Flyway existed.
--
-- Generated from the live database with:
--     mysqldump --no-data --skip-add-drop-table -h 127.0.0.1 -P 6606 -u root -p interviewer
--
-- CREATE TABLE was changed to CREATE TABLE IF NOT EXISTS; nothing else was edited.
-- An earlier version of this file was reconstructed from the entity classes and was
-- wrong in several ways - it missed `admin` entirely (no entity or mapper exists for
-- it), invented secondary indexes that do not exist, and had most columns nullable
-- when the real schema declares them NOT NULL.
--
-- On an existing database this migration is skipped: spring.flyway.baseline-on-migrate
-- is true and baselineVersion is 1, so Flyway records the current state as version 1
-- and applies from V2 onwards. This file runs only against an empty schema.
-- ---------------------------------------------------------------------------

-- Legacy: no Admin entity, mapper or controller exists in the backend.
-- Preserved because it is present in the live schema.
CREATE TABLE IF NOT EXISTS `admin` (
  `id` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `username` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Username',
  `password` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Password',
  `face` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Face image information',
  `real_name` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Administrator name',
  `remark` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Remarks',
  `created_time` datetime NOT NULL COMMENT 'Created time',
  `updated_time` datetime NOT NULL COMMENT 'Updated time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `username` (`username`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Admin level users for operations management platform';

CREATE TABLE IF NOT EXISTS `interviewer` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `ai_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Digital human interviewer name',
  `image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Digital human image photo',
  `create_time` datetime NOT NULL,
  `updated_time` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Digital human interviewer table';

CREATE TABLE IF NOT EXISTS `job` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `job_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Job name',
  `job_desc` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Job description',
  `status` int NOT NULL COMMENT '1: Job open\n2: Job closed',
  `interviewer_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Assigned digital human interviewer id, each job requires a corresponding interviewer to conduct the interview',
  `prompt` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Prefix prompt for the job interview result to be sent to ChatGLM',
  `create_time` datetime NOT NULL,
  `updated_time` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Job information table';

-- Note `created_time`, not `create_time` - this table is the naming outlier.
-- mobile and identity_num are both UNIQUE and NOT NULL.
CREATE TABLE IF NOT EXISTS `candidate` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `real_name` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Real name (encryption required)',
  `identity_num` varchar(18) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Candidate ID number (SSN)',
  `mobile` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Candidate mobile phone number',
  `sex` int DEFAULT NULL COMMENT 'Gender, 1:Male 0:Female 2:Not specified',
  `face` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Candidate photo',
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Email',
  `birthday` date DEFAULT NULL COMMENT 'Birthday',
  `country` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Country',
  `state` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'State',
  `city` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'City',
  `county` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'County',
  `address` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Address',
  `job_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Applied job primary key id',
  `remark` varchar(1280) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Remarks',
  `created_time` datetime NOT NULL COMMENT 'Created time',
  `updated_time` datetime NOT NULL COMMENT 'Updated time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `mobile` (`mobile`) USING BTREE,
  UNIQUE KEY `identity_num` (`identity_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Candidate table';

-- reference_answer is NOT NULL: every question must carry one.
CREATE TABLE IF NOT EXISTS `question_lib` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `question` varchar(1280) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Interview question (text content)',
  `reference_answer` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Reference answer',
  `ai_src` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Interview digital human corresponding address',
  `interviewer_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Assigned digital human interviewer id, each job requires a corresponding interviewer to conduct the interview',
  `is_on` int NOT NULL COMMENT '1: Enable this question\n0: Disable this question',
  `create_time` datetime NOT NULL,
  `updated_time` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Interview question library table (each digital human interviewer corresponds to some interview questions)';

-- answer_content is varchar(6000) - a real ceiling on transcript length.
CREATE TABLE IF NOT EXISTS `interview_record` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `candidate_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Candidate id',
  `job_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Job name, snapshot name, original field may change',
  `answer_content` varchar(6000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Candidate answer content',
  `take_time` int NOT NULL COMMENT 'Total time spent on the entire interview, unit: seconds',
  `result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Interview result details',
  `create_time` datetime NOT NULL,
  `updated_time` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Interview record table';
