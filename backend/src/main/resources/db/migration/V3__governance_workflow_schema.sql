-- V3__governance_workflow_schema.sql
-- Submissions, Content Versions, Analysis Runs, Findings, Review Decisions, and Audit Trail

CREATE TABLE IF NOT EXISTS submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    current_version_id BIGINT NULL,
    title VARCHAR(191) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT', -- DRAFT, IN_REVIEW, CHANGES_REQUESTED, APPROVED, REJECTED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_submissions_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT,
    CONSTRAINT fk_submissions_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS content_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    raw_text LONGTEXT NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_content_versions_submission FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_content_versions_user FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_submission_version_number UNIQUE (submission_id, version_number)
);

-- Link submissions.current_version_id to content_versions(id)
ALTER TABLE submissions
    ADD CONSTRAINT fk_submissions_current_version
    FOREIGN KEY (current_version_id) REFERENCES content_versions(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS analysis_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_version_id BIGINT NOT NULL,
    initiated_by_user_id BIGINT NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_analysis_runs_version FOREIGN KEY (content_version_id) REFERENCES content_versions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_analysis_runs_user FOREIGN KEY (initiated_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS findings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_run_id BIGINT NOT NULL,
    content_version_id BIGINT NOT NULL,
    knowledge_entry_id BIGINT NULL,
    source_version_id BIGINT NULL,
    type VARCHAR(30) NOT NULL, -- VIOLATION, WARNING, ADVISORY
    category VARCHAR(30) NOT NULL, -- TONE, TERMINOLOGY, MESSAGING, FACT, CLAIM, VISUAL, ACCESSIBILITY
    classification VARCHAR(35) NOT NULL, -- VERIFIED, NEEDS_HUMAN_JUDGEMENT, ADVISORY, REJECTED_BY_VALIDATOR
    severity VARCHAR(20) NOT NULL, -- CRITICAL, MAJOR, MINOR, ADVISORY
    passage_start INT NOT NULL,
    passage_end INT NOT NULL,
    passage_quote TEXT NOT NULL,
    evidence_quote TEXT NULL,
    explanation TEXT NOT NULL,
    suggested_edit TEXT NULL,
    confidence VARCHAR(20) NOT NULL DEFAULT 'HIGH',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_findings_analysis_run FOREIGN KEY (analysis_run_id) REFERENCES analysis_runs(id) ON DELETE CASCADE,
    CONSTRAINT fk_findings_content_version FOREIGN KEY (content_version_id) REFERENCES content_versions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_findings_knowledge_entry FOREIGN KEY (knowledge_entry_id) REFERENCES knowledge_entries(id) ON DELETE SET NULL,
    CONSTRAINT fk_findings_source_version FOREIGN KEY (source_version_id) REFERENCES source_versions(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS finding_responses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    finding_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL, -- ACCEPTED, MODIFIED, REJECTED
    modified_text TEXT NULL,
    reason TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_responses_finding FOREIGN KEY (finding_id) REFERENCES findings(id) ON DELETE CASCADE,
    CONSTRAINT fk_responses_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS review_decisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    content_version_id BIGINT NOT NULL,
    reviewer_user_id BIGINT NOT NULL,
    decision VARCHAR(30) NOT NULL, -- APPROVED, REJECTED, CHANGES_REQUESTED
    comment TEXT NULL,
    override_reason TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_decisions_submission FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_decisions_version FOREIGN KEY (content_version_id) REFERENCES content_versions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_decisions_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    finding_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_comments_submission FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_finding FOREIGN KEY (finding_id) REFERENCES findings(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id BIGINT NOT NULL,
    metadata TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_submissions_workspace ON submissions(workspace_id);
CREATE INDEX idx_content_versions_submission ON content_versions(submission_id);
CREATE INDEX idx_findings_content_version ON findings(content_version_id);
CREATE INDEX idx_findings_classification ON findings(classification);
CREATE INDEX idx_audit_workspace ON audit_events(workspace_id);
