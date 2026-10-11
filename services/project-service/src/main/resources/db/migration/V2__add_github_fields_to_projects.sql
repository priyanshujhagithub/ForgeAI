ALTER TABLE projects
    ADD COLUMN github_repo_url VARCHAR(500),
    ADD COLUMN github_repo_name VARCHAR(255),
    ADD COLUMN github_owner VARCHAR(255);