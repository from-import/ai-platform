USE ai_platform;

-- Existing users remain ordinary users. Promote administrators explicitly after
-- this migration with: UPDATE app_user SET role = 'ADMIN' WHERE username = '...';
ALTER TABLE app_user
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER' AFTER status,
    ADD CONSTRAINT chk_app_user_role
        CHECK (role IN ('USER', 'ADMIN'));

-- Historical request records have no known owner, so user_id is nullable during
-- the migration. New application code should always populate it.
ALTER TABLE llm_request_record
    ADD COLUMN user_id BIGINT UNSIGNED NULL AFTER id,
    ADD KEY idx_llm_request_record_user_time (user_id, requested_at),
    ADD CONSTRAINT fk_llm_request_record_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
        ON DELETE RESTRICT;
