-- Minimal origin/develop-shaped schema used by the MySQL migration rehearsal.
-- It contains both a scored completed round and a MANUAL/MANUAL completed
-- round that intentionally has no criterion, assignment, review, or score.

CREATE TABLE contest (
    id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE user (
    id BIGINT NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE team (
    id BIGINT NOT NULL,
    contest_id BIGINT NOT NULL,
    participation_finalized_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE submission (
    id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE contest_stage (
    id BIGINT NOT NULL,
    contest_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    starts_at DATETIME(6) NULL,
    ends_at DATETIME(6) NULL,
    stage_type VARCHAR(30) NOT NULL,
    target_type VARCHAR(30) NULL,
    pass_rule VARCHAR(30) NULL,
    pass_count INT NULL,
    min_score DECIMAL(10, 2) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE contest_stage_entry (
    id BIGINT NOT NULL,
    contest_stage_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    final_score DECIMAL(10, 2) NULL,
    rank_no INT NULL,
    decision_type VARCHAR(20) NULL,
    decided_by_user_id BIGINT NULL,
    decision_reason TEXT NULL,
    finalized_at DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE review_criterion (
    id BIGINT NOT NULL,
    contest_stage_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    label VARCHAR(100) NOT NULL,
    max_score INT NOT NULL,
    sort_order INT NOT NULL,
    active BOOLEAN NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE contest_judge (
    id BIGINT NOT NULL,
    contest_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    name VARCHAR(50) NOT NULL,
    role_label VARCHAR(50) NOT NULL,
    review_token_hash VARCHAR(64) NOT NULL,
    token_expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE review_assignment (
    id BIGINT NOT NULL,
    contest_judge_id BIGINT NOT NULL,
    contest_stage_entry_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE review (
    id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    total_score DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE review_score_item (
    id BIGINT NOT NULL,
    score DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE award (
    id BIGINT NOT NULL,
    contest_stage_entry_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);

INSERT INTO contest (id, status) VALUES (1, 'REVIEWING');
INSERT INTO user (id) VALUES (10), (11);
INSERT INTO team (
    id,
    contest_id,
    participation_finalized_at
) VALUES
(900, 1, '2026-06-30 00:00:00.000000'),
(901, 1, '2026-06-30 00:00:00.000000');
INSERT INTO submission (id, team_id)
VALUES (100, 900), (101, 901);

INSERT INTO contest_stage (
    id,
    contest_id,
    sequence_no,
    name,
    status,
    starts_at,
    ends_at,
    stage_type,
    target_type,
    pass_rule,
    pass_count,
    min_score,
    created_at,
    updated_at
) VALUES
(
    1000,
    1,
    10,
    'Scored review',
    'COMPLETED',
    '2026-07-01 00:00:00.000000',
    '2026-07-02 00:00:00.000000',
    'REVIEW',
    'ALL_SUBMISSIONS',
    'TOP_N',
    1,
    NULL,
    '2026-07-01 00:00:00.000000',
    '2026-07-02 00:00:00.000000'
),
(
    1001,
    1,
    30,
    'Manual decision',
    'COMPLETED',
    '2026-07-03 00:00:00.000000',
    '2026-07-04 00:00:00.000000',
    'PRESENTATION',
    'MANUAL',
    'MANUAL',
    NULL,
    NULL,
    '2026-07-03 00:00:00.000000',
    '2026-07-04 00:00:00.000000'
);

INSERT INTO contest_stage_entry (
    id,
    contest_stage_id,
    submission_id,
    status,
    final_score,
    rank_no,
    decision_type,
    decided_by_user_id,
    decision_reason,
    finalized_at,
    created_at,
    updated_at
) VALUES
(
    3000,
    1000,
    100,
    'PASSED',
    88.50,
    1,
    'RULE',
    NULL,
    NULL,
    '2026-07-02 00:00:00.000000',
    '2026-07-01 00:00:00.000000',
    '2026-07-02 00:00:00.000000'
),
(
    3001,
    1001,
    101,
    'PASSED',
    NULL,
    1,
    'MANUAL',
    10,
    '관리자 수동 선정',
    '2026-07-04 00:00:00.000000',
    '2026-07-03 00:00:00.000000',
    '2026-07-04 00:00:00.000000'
);

INSERT INTO review_criterion (
    id,
    contest_stage_id,
    code,
    label,
    max_score,
    sort_order,
    active
) VALUES (2000, 1000, 'quality', '완성도', 100, 1, TRUE);

INSERT INTO contest_judge (
    id,
    contest_id,
    user_id,
    name,
    role_label,
    review_token_hash,
    token_expires_at,
    created_at
) VALUES (
    4000,
    1,
    11,
    '심사위원',
    '전문 심사위원',
    REPEAT('a', 64),
    '2026-08-01 00:00:00.000000',
    '2026-07-01 00:00:00.000000'
);

INSERT INTO review_assignment (
    id,
    contest_judge_id,
    contest_stage_entry_id,
    status
) VALUES (5000, 4000, 3000, 'COMPLETED');

INSERT INTO review (
    id,
    assignment_id,
    total_score
) VALUES (6000, 5000, 88.50);

INSERT INTO award (
    id,
    contest_stage_entry_id,
    team_id,
    status,
    confirmed_at
) VALUES (8000, 3001, 901, 'CANDIDATE', NULL);
