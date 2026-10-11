CREATE TABLE agent_runs (
    id UUID PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    task TEXT NOT NULL,
    status VARCHAR(40) NOT NULL,

    -- Structured results from the AI service, stored as JSON text.
    analysis_json TEXT,
    plan_json TEXT,
    result_json TEXT,

    base_branch VARCHAR(255),
    base_sha VARCHAR(64),
    branch_name VARCHAR(255),
    pull_request_number INTEGER,
    pull_request_url VARCHAR(500),

    error_message TEXT,
    sandbox_seconds DOUBLE PRECISION,
    estimated_cost_usd DOUBLE PRECISION,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,

    CONSTRAINT fk_agent_runs_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_agent_runs_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_agent_runs_project_created
    ON agent_runs (project_id, created_at DESC);

-- At most one run in flight per project. The service checks first; this index
-- is what makes the rule hold when two requests arrive at the same moment.
CREATE UNIQUE INDEX uq_agent_runs_one_active_per_project
    ON agent_runs (project_id)
    WHERE status IN (
        'QUEUED',
        'ANALYZING',
        'PLANNING',
        'GENERATING_AND_TESTING',
        'OPENING_PR'
    );
