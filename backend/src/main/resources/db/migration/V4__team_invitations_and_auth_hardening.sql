-- V4__team_invitations_and_auth_hardening.sql
-- Adds email verification status, token versioning for session revocation,
-- workspace team invitations, and authentication verification challenge tokens.

ALTER TABLE users ADD COLUMN email_verified BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE users ADD COLUMN token_version INT DEFAULT 1 NOT NULL;

CREATE TABLE IF NOT EXISTS workspace_invitations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    email VARCHAR(191) NOT NULL,
    role VARCHAR(30) NOT NULL,
    token VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    invited_by_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP NULL,
    CONSTRAINT fk_invitations_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    CONSTRAINT fk_invitations_inviter FOREIGN KEY (invited_by_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_invitations_token ON workspace_invitations(token);
CREATE INDEX idx_invitations_email ON workspace_invitations(email);
CREATE INDEX idx_invitations_workspace ON workspace_invitations(workspace_id);

CREATE TABLE IF NOT EXISTS auth_verification_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(191) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    token_type VARCHAR(50) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempts INT DEFAULT 0 NOT NULL,
    max_attempts INT DEFAULT 5 NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_auth_tokens_email_type ON auth_verification_tokens(email, token_type);
