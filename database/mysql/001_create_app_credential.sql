CREATE DATABASE IF NOT EXISTS ai_platform
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE ai_platform;

CREATE TABLE IF NOT EXISTS app_credential (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    app_id VARCHAR(64) NOT NULL,
    app_name VARCHAR(128) NOT NULL,
    api_key_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    rate_limit_per_minute INT UNSIGNED NOT NULL DEFAULT 60,
    daily_token_quota BIGINT UNSIGNED NOT NULL DEFAULT 100000,
    allowed_model_aliases JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_credential_app_id (app_id),
    UNIQUE KEY uk_app_credential_api_key_hash (api_key_hash),
    CONSTRAINT chk_app_credential_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED'))
);
