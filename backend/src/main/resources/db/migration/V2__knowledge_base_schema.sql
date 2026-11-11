-- V2__knowledge_base_schema.sql
-- Knowledge Base Schema: Sources, Versions, and Entries

CREATE TABLE IF NOT EXISTS knowledge_sources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    current_version_id BIGINT NULL,
    title VARCHAR(191) NOT NULL,
    type VARCHAR(40) NOT NULL, -- GUIDELINE, TERMINOLOGY, CLAIMS_REGISTER, ASSET_RULES
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_sources_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS source_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(20) NOT NULL, -- DRAFT, CURRENT, SUPERSEDED
    checksum VARCHAR(64) NULL, -- Canonical SHA-256 hex digest
    file_reference VARCHAR(255) NULL, -- Historical document reference specific to this version
    approved_by_user_id BIGINT NULL,
    approved_at TIMESTAMP NULL,
    effective_from TIMESTAMP NULL,
    effective_to TIMESTAMP NULL,
    changelog_notes TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_versions_source FOREIGN KEY (source_id) REFERENCES knowledge_sources(id) ON DELETE RESTRICT,
    CONSTRAINT fk_versions_approver FOREIGN KEY (approved_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_source_version_number UNIQUE (source_id, version_number),
    CONSTRAINT uk_source_version_id UNIQUE (source_id, id)
);

-- Authoritative composite foreign key: strictly binds current_version_id to a version belonging to the SAME source
ALTER TABLE knowledge_sources 
    ADD CONSTRAINT fk_sources_current_version 
    FOREIGN KEY (id, current_version_id) REFERENCES source_versions(source_id, id) ON DELETE RESTRICT;

CREATE TABLE IF NOT EXISTS knowledge_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_version_id BIGINT NOT NULL,
    code VARCHAR(60) NOT NULL, -- e.g. TONE-01, CLAIM-C014
    category VARCHAR(30) NOT NULL, -- TONE, TERMINOLOGY, MESSAGING, FACT, CLAIM, VISUAL, ACCESSIBILITY
    default_severity VARCHAR(20) NOT NULL, -- CRITICAL, MAJOR, MINOR, ADVISORY
    text TEXT NOT NULL,
    page_or_section_reference VARCHAR(120) NULL, -- e.g. "Page 4, §2.1"
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_entries_version FOREIGN KEY (source_version_id) REFERENCES source_versions(id) ON DELETE RESTRICT,
    CONSTRAINT uk_version_entry_code UNIQUE (source_version_id, code)
);

CREATE INDEX idx_sources_workspace ON knowledge_sources(workspace_id);
CREATE INDEX idx_versions_source_status ON source_versions(source_id, status);
CREATE INDEX idx_entries_version_category ON knowledge_entries(source_version_id, category);
