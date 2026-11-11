package com.veriweave.knowledge.entity;

/**
 * Version lifecycle statuses specified in Round 1 Proposal §8:
 * - DRAFT: Working draft, reviewable and editable by ADMIN; not active for verification.
 * - CURRENT: Active, approved single source-of-truth version used by the verification engine.
 * - SUPERSEDED: Archived historical approved version, immutable for finding traceability.
 */
public enum VersionStatus {
    DRAFT,
    CURRENT,
    SUPERSEDED
}
