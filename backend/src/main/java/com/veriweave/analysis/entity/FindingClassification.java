package com.veriweave.analysis.entity;

/**
 * The four finding classifications defined in Proposal §9:
 * - VERIFIED: Passed deterministic citation & passage validation against a CURRENT source version.
 * - NEEDS_HUMAN_JUDGEMENT: Valid citation, but rule is ambiguous, confidence is low, or sources conflict.
 * - ADVISORY: Suggestion without an approved brand rule. Labelled "AI opinion, not a brand rule". Never blocks.
 * - REJECTED_BY_VALIDATOR: Failed deterministic verification (hallucinated quote, nonexistent rule, etc.). Discarded.
 */
public enum FindingClassification {
    VERIFIED,
    NEEDS_HUMAN_JUDGEMENT,
    ADVISORY,
    REJECTED_BY_VALIDATOR
}
