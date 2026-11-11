package com.veriweave.knowledge.entity;

/**
 * Knowledge source types specified in Round 1 Proposal §7 & §8:
 * - GUIDELINE: Brand guidelines, tone of voice, style guides
 * - TERMINOLOGY: Preferred & prohibited terms, naming conventions
 * - CLAIMS_REGISTER: Approved claims, clinical/scientific claims register
 * - ASSET_RULES: Logo usage, color palette, visual/accessibility constraints
 */
public enum SourceType {
    GUIDELINE,
    TERMINOLOGY,
    CLAIMS_REGISTER,
    ASSET_RULES
}
