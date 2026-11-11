package com.veriweave.knowledge.entity;

/**
 * The four severity levels defined in Proposal §8 & §9:
 * - CRITICAL: High-risk regulatory, factual, or forbidden claim violation. Blocks approval unless explicitly overridden.
 * - MAJOR: Direct violation of an approved rule or terminology constraint. Blocks approval unless resolved.
 * - MINOR: Style drift, punctuation, or secondary guideline deviation.
 * - ADVISORY: Non-blocking advisory finding or AI opinion. Never blocks approval.
 */
public enum Severity {
    CRITICAL,
    MAJOR,
    MINOR,
    ADVISORY
}
