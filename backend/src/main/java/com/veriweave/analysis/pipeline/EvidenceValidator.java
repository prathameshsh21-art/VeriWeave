package com.veriweave.analysis.pipeline;

import com.veriweave.analysis.entity.Finding;
import com.veriweave.analysis.entity.FindingClassification;
import com.veriweave.knowledge.entity.KnowledgeEntry;
import com.veriweave.knowledge.entity.SourceVersion;
import com.veriweave.knowledge.entity.VersionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
public class EvidenceValidator {

    /**
     * Validates a proposed finding against the raw submission text and active knowledge base.
     * Enforces the core principle: "The model proposes. Deterministic code verifies. A human decides."
     */
    public Finding validateAndClassify(
            ProposedFinding proposal,
            String rawSubmissionText,
            Long workspaceId,
            Map<Long, KnowledgeEntry> retrievedCandidateMap
    ) {
        // Validation Check 1: Null & Basic Structure
        if (proposal == null) {
            return null;
        }

        // Handle Advisory (AI opinion with no cited brand rule)
        if (proposal.isAdvisoryOnly() || proposal.getKnowledgeEntryId() == null) {
            return buildAdvisoryFinding(proposal, rawSubmissionText);
        }

        // Validation Check 2: Character Offset Boundaries
        Integer start = proposal.getPassageStart();
        Integer end = proposal.getPassageEnd();
        if (start == null || end == null || start < 0 || end > rawSubmissionText.length() || start >= end) {
            log.warn("[VALIDATION_FAILED] Invalid character offsets: start={}, end={}, textLength={}", 
                    start, end, rawSubmissionText.length());
            return buildRejectedFinding(proposal, "Invalid character offsets in submission text");
        }

        // Validation Check 3: Verbatim Passage Quote Verification
        String actualPassageInText = rawSubmissionText.substring(start, end);
        if (!actualPassageInText.equals(proposal.getPassageQuote())) {
            log.warn("[VALIDATION_FAILED] Passage quote mismatch! Quoted: '{}', Actual at offsets [{}, {}]: '{}'",
                    proposal.getPassageQuote(), start, end, actualPassageInText);
            return buildRejectedFinding(proposal, "Quoted passage does not match submission text at stated offsets");
        }

        // Validation Check 4: Knowledge Entry Retrieved & Workspace Scoped
        KnowledgeEntry entry = retrievedCandidateMap.get(proposal.getKnowledgeEntryId());
        if (entry == null) {
            log.warn("[VALIDATION_FAILED] Cited KnowledgeEntry ID {} was not retrieved or does not exist", 
                    proposal.getKnowledgeEntryId());
            return buildRejectedFinding(proposal, "Referenced rule was not among the retrieved candidate set");
        }

        SourceVersion version = entry.getSourceVersion();
        if (version == null) {
            log.warn("[VALIDATION_FAILED] Entry {} has no parent SourceVersion", entry.getId());
            return buildRejectedFinding(proposal, "Referenced entry is detached from a source version");
        }

        if (version.getSource() == null || !version.getSource().getWorkspace().getId().equals(workspaceId)) {
            log.warn("[VALIDATION_FAILED] Workspace mismatch! Entry workspace does not match active workspace {}", workspaceId);
            return buildRejectedFinding(proposal, "Cross-workspace rule citation is strictly disallowed");
        }

        // Validation Check 5: Source Version MUST be CURRENT and Effective
        if (version.getStatus() != VersionStatus.CURRENT) {
            log.warn("[VALIDATION_FAILED] Version status is {} (MUST be CURRENT)", version.getStatus());
            return buildRejectedFinding(proposal, "Cited source version is " + version.getStatus() + "; only CURRENT versions are approved evidence");
        }

        Instant now = Instant.now();
        if (version.getEffectiveTo() != null && version.getEffectiveTo().isBefore(now)) {
            log.warn("[VALIDATION_FAILED] Version expired on {}", version.getEffectiveTo());
            return buildRejectedFinding(proposal, "Cited source version has expired");
        }

        // Validation Check 6: Evidence Quote Substring Verification
        String evidenceQuote = proposal.getEvidenceQuote();
        if (evidenceQuote != null && !evidenceQuote.isBlank()) {
            if (!entry.getText().toLowerCase().contains(evidenceQuote.trim().toLowerCase())) {
                log.warn("[VALIDATION_FAILED] Evidence quote '{}' not found in entry rule text '{}'",
                        evidenceQuote, entry.getText());
                return buildRejectedFinding(proposal, "Evidence quote is not a substring of the cited knowledge entry");
            }
        }

        // Validation Check 7: Category & Severity Invariants
        // Enforce severity directly from the approved entry, never arbitrary LLM values
        FindingClassification classification = FindingClassification.VERIFIED;
        if ("LOW".equalsIgnoreCase(proposal.getConfidence())) {
            classification = FindingClassification.NEEDS_HUMAN_JUDGEMENT;
        }

        return Finding.builder()
                .passageStart(start)
                .passageEnd(end)
                .passageQuote(actualPassageInText)
                .knowledgeEntry(entry)
                .sourceVersion(version)
                .type(proposal.getType())
                .category(entry.getCategory()) // Authoritative from entry
                .severity(entry.getDefaultSeverity()) // Authoritative from entry
                .classification(classification)
                .evidenceQuote(evidenceQuote != null ? evidenceQuote.trim() : entry.getText())
                .explanation(proposal.getExplanation())
                .suggestedEdit(proposal.getSuggestedEdit())
                .confidence(proposal.getConfidence())
                .build();
    }

    private Finding buildAdvisoryFinding(ProposedFinding proposal, String rawText) {
        int start = (proposal.getPassageStart() != null && proposal.getPassageStart() >= 0 && proposal.getPassageStart() < rawText.length())
                ? proposal.getPassageStart() : 0;
        int end = (proposal.getPassageEnd() != null && proposal.getPassageEnd() > start && proposal.getPassageEnd() <= rawText.length())
                ? proposal.getPassageEnd() : Math.min(rawText.length(), start + 20);

        return Finding.builder()
                .passageStart(start)
                .passageEnd(end)
                .passageQuote(rawText.substring(start, end))
                .knowledgeEntry(null)
                .sourceVersion(null)
                .type(proposal.getType())
                .category(proposal.getCategory())
                .severity(com.veriweave.knowledge.entity.Severity.ADVISORY)
                .classification(FindingClassification.ADVISORY)
                .evidenceQuote("No approved support found (AI opinion, not a brand rule)")
                .explanation(proposal.getExplanation() != null ? proposal.getExplanation() : "Advisory observation: no approved guideline exists for this topic.")
                .suggestedEdit(proposal.getSuggestedEdit())
                .confidence("LOW")
                .build();
    }

    private Finding buildRejectedFinding(ProposedFinding proposal, String reason) {
        return Finding.builder()
                .passageStart(proposal.getPassageStart() != null ? proposal.getPassageStart() : 0)
                .passageEnd(proposal.getPassageEnd() != null ? proposal.getPassageEnd() : 0)
                .passageQuote(proposal.getPassageQuote() != null ? proposal.getPassageQuote() : "")
                .classification(FindingClassification.REJECTED_BY_VALIDATOR)
                .severity(com.veriweave.knowledge.entity.Severity.ADVISORY)
                .type(proposal.getType())
                .category(proposal.getCategory())
                .explanation("Rejected by deterministic validator: " + reason)
                .confidence("NONE")
                .build();
    }
}
