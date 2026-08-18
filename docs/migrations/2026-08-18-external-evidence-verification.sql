-- External evidence manual verification MVP (MySQL 8)
CREATE TABLE evidence_submission (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    organization_id BIGINT NOT NULL,
    submitted_by BIGINT NOT NULL,
    evidence_type VARCHAR(40) NOT NULL,
    target_record_type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    issuer_name VARCHAR(200) NOT NULL,
    issuer_code VARCHAR(100) NULL,
    credential_number_hash VARCHAR(64) NULL,
    credential_number_last4 VARCHAR(4) NULL,
    numeric_value DECIMAL(10,2) NULL,
    issued_at DATE NULL,
    expires_at DATE NULL,
    submitted_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT uk_evidence_submission_public UNIQUE (public_id),
    CONSTRAINT fk_evidence_submission_org FOREIGN KEY (organization_id) REFERENCES organization(id),
    CONSTRAINT fk_evidence_submission_user FOREIGN KEY (submitted_by) REFERENCES user(id),
    INDEX idx_evidence_submission_user (submitted_by, created_at),
    INDEX idx_evidence_submission_org_status (organization_id, status, created_at),
    INDEX idx_evidence_submission_duplicate (issuer_code, credential_number_hash)
);

CREATE TABLE evidence_file (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    submission_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    safety_status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT uk_evidence_file_public UNIQUE (public_id),
    CONSTRAINT uk_evidence_file_storage UNIQUE (storage_key),
    CONSTRAINT uk_evidence_file_submission_hash UNIQUE (submission_id, sha256),
    CONSTRAINT fk_evidence_file_submission FOREIGN KEY (submission_id) REFERENCES evidence_submission(id)
);

CREATE TABLE evidence_verification_case (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    submission_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    required_assurance_level VARCHAR(5) NOT NULL,
    achieved_assurance_level VARCHAR(5) NULL,
    opened_at DATETIME(6) NOT NULL,
    closed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT uk_evidence_case_public UNIQUE (public_id),
    CONSTRAINT uk_evidence_case_submission UNIQUE (submission_id),
    CONSTRAINT fk_evidence_case_submission FOREIGN KEY (submission_id) REFERENCES evidence_submission(id),
    INDEX idx_evidence_case_queue (status, opened_at)
);

CREATE TABLE evidence_verification_review (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    case_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    result VARCHAR(20) NOT NULL,
    assurance_level VARCHAR(5) NOT NULL,
    reason_code VARCHAR(100) NOT NULL,
    official_reference_url VARCHAR(1000) NULL,
    note VARCHAR(1000) NULL,
    reviewed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT uk_evidence_review_public UNIQUE (public_id),
    CONSTRAINT uk_evidence_review_case_reviewer UNIQUE (case_id, reviewer_id),
    CONSTRAINT fk_evidence_review_case FOREIGN KEY (case_id) REFERENCES evidence_verification_case(id),
    CONSTRAINT fk_evidence_review_user FOREIGN KEY (reviewer_id) REFERENCES user(id)
);

CREATE TABLE evidence_verification_decision (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    case_id BIGINT NOT NULL,
    decision VARCHAR(30) NOT NULL,
    assurance_level VARCHAR(5) NOT NULL,
    reason_code VARCHAR(100) NOT NULL,
    reviewer_primary_id BIGINT NOT NULL,
    reviewer_secondary_id BIGINT NOT NULL,
    decided_at DATETIME(6) NOT NULL,
    bundle_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT uk_evidence_decision_public UNIQUE (public_id),
    CONSTRAINT uk_evidence_decision_case UNIQUE (case_id),
    CONSTRAINT fk_evidence_decision_case FOREIGN KEY (case_id) REFERENCES evidence_verification_case(id),
    CONSTRAINT fk_evidence_decision_primary FOREIGN KEY (reviewer_primary_id) REFERENCES user(id),
    CONSTRAINT fk_evidence_decision_secondary FOREIGN KEY (reviewer_secondary_id) REFERENCES user(id),
    CONSTRAINT chk_evidence_distinct_reviewers CHECK (reviewer_primary_id <> reviewer_secondary_id)
);

ALTER TABLE student_non_course_record
    ADD COLUMN verification_assurance_level VARCHAR(5) NULL AFTER verification_status,
    ADD COLUMN external_evidence_type VARCHAR(40) NULL AFTER verification_assurance_level,
    ADD COLUMN external_issuer_code VARCHAR(100) NULL AFTER external_evidence_type;

CREATE TABLE evidence_binding (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    decision_id BIGINT NOT NULL,
    student_non_course_record_id BIGINT NOT NULL,
    bound_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT uk_evidence_binding_decision UNIQUE (decision_id),
    CONSTRAINT uk_evidence_binding_non_course UNIQUE (student_non_course_record_id),
    CONSTRAINT fk_evidence_binding_decision FOREIGN KEY (decision_id) REFERENCES evidence_verification_decision(id),
    CONSTRAINT fk_evidence_binding_non_course FOREIGN KEY (student_non_course_record_id) REFERENCES student_non_course_record(id)
);
