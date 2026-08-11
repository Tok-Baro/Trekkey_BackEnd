-- Hansung graduation self-check schema (MySQL 8).
--
-- Preconditions:
--   1. Back up the database and stop application writes.
--   2. Confirm exactly one organization row represents 한성대학교.
--   3. Apply this migration before deploying the graduation entities.
--   4. Do not rely on Hibernate ddl-auto=update for these constraints.

SELECT COUNT(*) AS hansung_organization_count
FROM organization
WHERE name = '한성대학교';

SELECT COUNT(*) INTO @organization_code_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'organization'
  AND column_name = 'code';

SET @add_organization_code_sql = IF(
    @organization_code_exists = 0,
    'ALTER TABLE organization ADD COLUMN code VARCHAR(50) NULL AFTER public_id',
    'SELECT 1'
);
PREPARE add_organization_code_statement FROM @add_organization_code_sql;
EXECUTE add_organization_code_statement;
DEALLOCATE PREPARE add_organization_code_statement;

UPDATE organization
SET code = CONCAT('ORG_LEGACY_', id)
WHERE code IS NULL OR code = '';

UPDATE organization
SET code = 'HANSUNG_UNIVERSITY'
WHERE name = '한성대학교';

ALTER TABLE organization
    MODIFY COLUMN code VARCHAR(50) NOT NULL,
    ADD CONSTRAINT uk_organization_code UNIQUE (code);

CREATE TABLE academic_unit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    organization_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    external_code VARCHAR(50) NULL,
    name VARCHAR(150) NOT NULL,
    unit_type VARCHAR(30) NOT NULL,
    valid_from_year SMALLINT NOT NULL,
    valid_to_year SMALLINT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_academic_unit_public_id UNIQUE (public_id),
    CONSTRAINT uk_academic_unit_org_external_code UNIQUE (organization_id, external_code),
    CONSTRAINT fk_academic_unit_organization FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE RESTRICT,
    CONSTRAINT fk_academic_unit_parent FOREIGN KEY (parent_id) REFERENCES academic_unit(id) ON DELETE RESTRICT,
    CONSTRAINT ck_academic_unit_years CHECK (valid_to_year IS NULL OR valid_to_year >= valid_from_year),
    INDEX idx_academic_unit_org_status_name (organization_id, status, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE academic_course (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    organization_id BIGINT NOT NULL,
    academic_year SMALLINT NOT NULL,
    course_code VARCHAR(30) NOT NULL,
    name VARCHAR(200) NOT NULL,
    credits DECIMAL(4,1) NOT NULL,
    category VARCHAR(40) NOT NULL,
    academic_unit_id BIGINT NULL,
    distribution_area VARCHAR(40) NULL,
    valid_from_term VARCHAR(6) NOT NULL,
    valid_to_term VARCHAR(6) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_academic_course_public_id UNIQUE (public_id),
    CONSTRAINT uk_academic_course_org_year_code UNIQUE (organization_id, academic_year, course_code),
    CONSTRAINT fk_academic_course_organization FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE RESTRICT,
    CONSTRAINT fk_academic_course_unit FOREIGN KEY (academic_unit_id) REFERENCES academic_unit(id) ON DELETE RESTRICT,
    CONSTRAINT ck_academic_course_credits CHECK (credits > 0),
    INDEX idx_academic_course_org_name (organization_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE graduation_policy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    organization_id BIGINT NOT NULL,
    academic_unit_id BIGINT NULL,
    policy_code VARCHAR(100) NOT NULL,
    version_no INT NOT NULL,
    policy_type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    admission_year_from SMALLINT NULL,
    admission_year_to SMALLINT NULL,
    admission_type VARCHAR(30) NULL,
    graduation_path VARCHAR(40) NULL,
    major_plan_type VARCHAR(50) NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    status VARCHAR(20) NOT NULL,
    source_set_hash CHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    published_at DATETIME(6) NULL,
    supersedes_id BIGINT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_graduation_policy_public_id UNIQUE (public_id),
    CONSTRAINT uk_graduation_policy_org_code_version UNIQUE (organization_id, policy_code, version_no),
    CONSTRAINT fk_graduation_policy_organization FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE RESTRICT,
    CONSTRAINT fk_graduation_policy_unit FOREIGN KEY (academic_unit_id) REFERENCES academic_unit(id) ON DELETE RESTRICT,
    CONSTRAINT fk_graduation_policy_created_by FOREIGN KEY (created_by) REFERENCES `user`(id) ON DELETE RESTRICT,
    CONSTRAINT fk_graduation_policy_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES `user`(id) ON DELETE RESTRICT,
    CONSTRAINT fk_graduation_policy_supersedes FOREIGN KEY (supersedes_id) REFERENCES graduation_policy(id) ON DELETE RESTRICT,
    CONSTRAINT ck_graduation_policy_version CHECK (version_no >= 1),
    CONSTRAINT ck_graduation_policy_admission_years CHECK (admission_year_to IS NULL OR admission_year_from IS NULL OR admission_year_to >= admission_year_from),
    CONSTRAINT ck_graduation_policy_effective_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    INDEX idx_graduation_policy_resolution (organization_id, status, admission_year_from, admission_year_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE graduation_policy_source (
    id BIGINT NOT NULL AUTO_INCREMENT,
    policy_id BIGINT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    official_url VARCHAR(1000) NOT NULL,
    title VARCHAR(300) NOT NULL,
    published_at DATE NULL,
    retrieved_at DATETIME(6) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    source_locator VARCHAR(300) NULL,
    stored_object_key VARCHAR(500) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_policy_source_hash_locator UNIQUE (policy_id, content_hash, source_locator),
    CONSTRAINT fk_policy_source_policy FOREIGN KEY (policy_id) REFERENCES graduation_policy(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE graduation_requirement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    policy_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    requirement_code VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    node_type VARCHAR(20) NOT NULL,
    operator_type VARCHAR(20) NULL,
    rule_type VARCHAR(50) NULL,
    parameters_json JSON NOT NULL,
    sequence_no INT NOT NULL,
    required BOOLEAN NOT NULL,
    source_id BIGINT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_graduation_requirement_policy_code UNIQUE (policy_id, requirement_code),
    CONSTRAINT fk_graduation_requirement_policy FOREIGN KEY (policy_id) REFERENCES graduation_policy(id) ON DELETE RESTRICT,
    CONSTRAINT fk_graduation_requirement_parent FOREIGN KEY (parent_id) REFERENCES graduation_requirement(id) ON DELETE RESTRICT,
    CONSTRAINT fk_graduation_requirement_source FOREIGN KEY (source_id) REFERENCES graduation_policy_source(id) ON DELETE RESTRICT,
    CONSTRAINT ck_graduation_requirement_sequence CHECK (sequence_no >= 1),
    CONSTRAINT ck_graduation_requirement_shape CHECK (
        (node_type = 'GROUP' AND operator_type IS NOT NULL AND rule_type IS NULL)
        OR (node_type = 'RULE' AND operator_type IS NULL AND rule_type IS NOT NULL)
    ),
    INDEX idx_graduation_requirement_tree (policy_id, parent_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student_academic_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    admission_year SMALLINT NOT NULL,
    curriculum_year SMALLINT NOT NULL,
    admission_type VARCHAR(30) NOT NULL,
    graduation_path VARCHAR(40) NOT NULL,
    major_plan_type VARCHAR(50) NOT NULL,
    registered_semesters SMALLINT NOT NULL,
    total_credits DECIMAL(5,1) NOT NULL,
    hansung_credits DECIMAL(5,1) NOT NULL,
    transfer_recognized_credits DECIMAL(5,1) NOT NULL,
    cumulative_gpa DECIMAL(3,2) NOT NULL,
    gpa_scale DECIMAL(2,1) NOT NULL,
    activity_points INT NOT NULL,
    international_student BOOLEAN NOT NULL,
    teaching_program BOOLEAN NOT NULL,
    input_mode VARCHAR(30) NOT NULL,
    record_completeness VARCHAR(20) NOT NULL,
    summary_as_of_term VARCHAR(6) NULL,
    fail_history_status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_student_profile_public_id UNIQUE (public_id),
    CONSTRAINT uk_student_profile_user UNIQUE (user_id),
    CONSTRAINT fk_student_profile_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE RESTRICT,
    CONSTRAINT ck_student_profile_nonnegative CHECK (
        registered_semesters >= 0 AND total_credits >= 0 AND hansung_credits >= 0
        AND transfer_recognized_credits >= 0 AND cumulative_gpa >= 0
        AND gpa_scale > 0 AND activity_points >= 0
    ),
    CONSTRAINT ck_student_profile_gpa CHECK (cumulative_gpa <= gpa_scale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student_academic_unit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    academic_unit_id BIGINT NOT NULL,
    role_type VARCHAR(30) NOT NULL,
    sequence_no INT NOT NULL,
    graduation_evidence_status VARCHAR(30) NOT NULL,
    verified_by BIGINT NULL,
    verified_at DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_student_academic_unit_role UNIQUE (profile_id, academic_unit_id, role_type),
    CONSTRAINT fk_student_academic_unit_profile FOREIGN KEY (profile_id) REFERENCES student_academic_profile(id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_academic_unit_unit FOREIGN KEY (academic_unit_id) REFERENCES academic_unit(id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_academic_unit_verifier FOREIGN KEY (verified_by) REFERENCES `user`(id) ON DELETE RESTRICT,
    CONSTRAINT ck_student_academic_unit_sequence CHECK (sequence_no >= 1),
    CONSTRAINT ck_student_academic_unit_verification CHECK (
        graduation_evidence_status <> 'UNIVERSITY_VERIFIED' OR (verified_by IS NOT NULL AND verified_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student_course_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    profile_id BIGINT NOT NULL,
    academic_course_id BIGINT NULL,
    term VARCHAR(6) NOT NULL,
    course_code VARCHAR(30) NULL,
    course_name VARCHAR(200) NOT NULL,
    credits DECIMAL(4,1) NOT NULL,
    grade VARCHAR(5) NULL,
    completion_status VARCHAR(20) NOT NULL,
    category VARCHAR(40) NOT NULL,
    academic_unit_id BIGINT NULL,
    mapping_status VARCHAR(30) NOT NULL,
    retake_group_key VARCHAR(100) NULL,
    source_type VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_student_course_public_id UNIQUE (public_id),
    CONSTRAINT uk_student_course_term_code UNIQUE (profile_id, term, course_code),
    CONSTRAINT fk_student_course_profile FOREIGN KEY (profile_id) REFERENCES student_academic_profile(id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_course_catalog FOREIGN KEY (academic_course_id) REFERENCES academic_course(id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_course_unit FOREIGN KEY (academic_unit_id) REFERENCES academic_unit(id) ON DELETE RESTRICT,
    CONSTRAINT ck_student_course_credits CHECK (credits > 0),
    INDEX idx_student_course_name_term (profile_id, course_name, term),
    INDEX idx_student_course_evaluation (profile_id, category, academic_unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student_non_course_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    profile_id BIGINT NOT NULL,
    record_type VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    numeric_value DECIMAL(10,2) NULL,
    issued_at DATE NULL,
    expires_at DATE NULL,
    verification_status VARCHAR(30) NOT NULL,
    verified_by BIGINT NULL,
    verified_at DATETIME(6) NULL,
    note VARCHAR(1000) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_student_non_course_public_id UNIQUE (public_id),
    CONSTRAINT fk_student_non_course_profile FOREIGN KEY (profile_id) REFERENCES student_academic_profile(id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_non_course_verifier FOREIGN KEY (verified_by) REFERENCES `user`(id) ON DELETE RESTRICT,
    CONSTRAINT ck_student_non_course_dates CHECK (expires_at IS NULL OR issued_at IS NULL OR expires_at >= issued_at),
    CONSTRAINT ck_student_non_course_verification CHECK (
        verification_status <> 'UNIVERSITY_VERIFIED' OR (verified_by IS NOT NULL AND verified_at IS NOT NULL)
    ),
    INDEX idx_student_non_course_evaluation (profile_id, record_type, verification_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE graduation_evaluation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    profile_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    policy_as_of DATE NOT NULL,
    input_hash CHAR(64) NOT NULL,
    input_snapshot_json JSON NOT NULL,
    profile_version BIGINT NOT NULL,
    evaluator_version VARCHAR(30) NOT NULL,
    satisfied_count INT NOT NULL,
    unsatisfied_count INT NOT NULL,
    unknown_count INT NOT NULL,
    evaluated_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_graduation_evaluation_public_id UNIQUE (public_id),
    CONSTRAINT fk_graduation_evaluation_profile FOREIGN KEY (profile_id) REFERENCES student_academic_profile(id) ON DELETE RESTRICT,
    CONSTRAINT ck_graduation_evaluation_counts CHECK (satisfied_count >= 0 AND unsatisfied_count >= 0 AND unknown_count >= 0),
    INDEX idx_graduation_evaluation_profile_time (profile_id, evaluated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE graduation_evaluation_policy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    evaluation_id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    policy_code_snapshot VARCHAR(100) NOT NULL,
    version_no_snapshot INT NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_evaluation_policy UNIQUE (evaluation_id, policy_id),
    CONSTRAINT fk_evaluation_policy_evaluation FOREIGN KEY (evaluation_id) REFERENCES graduation_evaluation(id) ON DELETE RESTRICT,
    CONSTRAINT fk_evaluation_policy_policy FOREIGN KEY (policy_id) REFERENCES graduation_policy(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE graduation_evaluation_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    evaluation_id BIGINT NOT NULL,
    requirement_id BIGINT NOT NULL,
    requirement_code VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    rule_type_snapshot VARCHAR(50) NULL,
    parameters_snapshot_json JSON NOT NULL,
    required_snapshot BOOLEAN NOT NULL,
    status VARCHAR(30) NOT NULL,
    current_value VARCHAR(100) NULL,
    required_value VARCHAR(100) NULL,
    remaining_value VARCHAR(100) NULL,
    message VARCHAR(1000) NOT NULL,
    source_url VARCHAR(1000) NULL,
    source_locator VARCHAR(300) NULL,
    sequence_no INT NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_evaluation_requirement_code UNIQUE (evaluation_id, requirement_code),
    CONSTRAINT fk_evaluation_item_evaluation FOREIGN KEY (evaluation_id) REFERENCES graduation_evaluation(id) ON DELETE RESTRICT,
    CONSTRAINT fk_evaluation_item_requirement FOREIGN KEY (requirement_id) REFERENCES graduation_requirement(id) ON DELETE RESTRICT,
    CONSTRAINT ck_evaluation_item_sequence CHECK (sequence_no >= 1),
    INDEX idx_evaluation_item_status_sequence (evaluation_id, status, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT code, COUNT(*) AS duplicate_count
FROM organization
GROUP BY code
HAVING COUNT(*) > 1;
