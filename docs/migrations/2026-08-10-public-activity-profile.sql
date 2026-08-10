CREATE TABLE IF NOT EXISTS public_activity_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    public_id VARCHAR(36) NOT NULL,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_public_activity_profile_user UNIQUE (user_id),
    CONSTRAINT uk_public_activity_profile_public_id UNIQUE (public_id),
    CONSTRAINT fk_public_activity_profile_user
        FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE,
    INDEX idx_public_activity_profile_lookup (public_id, enabled)
);
