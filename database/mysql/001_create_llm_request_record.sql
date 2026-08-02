CREATE DATABASE IF NOT EXISTS ai_platform
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE ai_platform;

CREATE TABLE IF NOT EXISTS llm_request_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(128) NOT NULL,
    result_status VARCHAR(16) NOT NULL,
    prompt_tokens INT UNSIGNED NULL,
    completion_tokens INT UNSIGNED NULL,
    total_tokens INT UNSIGNED NULL,
    latency_ms BIGINT UNSIGNED NOT NULL,
    upstream_status_code SMALLINT UNSIGNED NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(512) NULL,
    requested_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_llm_request_record_request_id (request_id),
    KEY idx_llm_request_record_requested_at (requested_at),
    KEY idx_llm_request_record_provider_model_time (provider, model, requested_at),
    CONSTRAINT chk_llm_request_record_result_status
        CHECK (result_status IN ('SUCCESS', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
