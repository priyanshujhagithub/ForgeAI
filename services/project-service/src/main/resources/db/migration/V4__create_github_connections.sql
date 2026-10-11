CREATE TABLE github_connections (
                                    id BIGSERIAL PRIMARY KEY,
                                    user_id BIGINT NOT NULL,
                                    github_user_id VARCHAR(100) NOT NULL,
                                    github_username VARCHAR(255) NOT NULL,
                                    access_token TEXT NOT NULL,
                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT fk_github_connections_user
                                        FOREIGN KEY (user_id)
                                            REFERENCES users(id)
                                            ON DELETE CASCADE,

                                    CONSTRAINT uq_github_connections_user
                                        UNIQUE (user_id)
);