ALTER TABLE github_connections
ALTER COLUMN github_user_id TYPE BIGINT
    USING github_user_id::BIGINT;