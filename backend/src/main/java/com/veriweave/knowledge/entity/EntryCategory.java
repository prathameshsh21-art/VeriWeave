package com.veriweave.knowledge.entity;

/**
 * Exactly the seven knowledge entry categories defined in Proposal §8:
 * - TONE: Tone of voice, empathy, writing style
 * - TERMINOLOGY: Required or prohibited terminology
 * - MESSAGING: Value propositions, core message pillars
 * - FACT: Verified company data, statistics, corporate facts
 * - CLAIM: Health, nutrition, environmental, or performance claims
 * - VISUAL: Logo placement, typography, image restrictions
 * - ACCESSIBILITY: Color contrast, alt-text presence
 */
public enum EntryCategory {
    TONE,
    TERMINOLOGY,
    MESSAGING,
    FACT,
    CLAIM,
    VISUAL,
    ACCESSIBILITY
}
