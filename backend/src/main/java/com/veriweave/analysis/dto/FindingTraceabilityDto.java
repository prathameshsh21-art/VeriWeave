package com.veriweave.analysis.dto;

import com.veriweave.analysis.entity.FindingClassification;
import com.veriweave.analysis.entity.FindingType;
import com.veriweave.audit.dto.AuditEventDto;
import com.veriweave.knowledge.entity.EntryCategory;
import com.veriweave.knowledge.entity.Severity;
import com.veriweave.review.entity.CreatorAction;
import com.veriweave.review.entity.ReviewDecisionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindingTraceabilityDto {
    // 1. Submitted Sentence & Content Context
    private Long submissionId;
    private String submissionTitle;
    private Long contentVersionId;
    private Integer contentVersionNumber;
    private String submissionAuthor;
    private Instant submissionCreatedAt;
    private String rawText;

    // 2. Deterministic Finding
    private Long findingId;
    private Long analysisRunId;
    private FindingType findingType;
    private EntryCategory category;
    private FindingClassification classification;
    private Severity severity;
    private Integer passageStart;
    private Integer passageEnd;
    private String passageQuote;
    private String explanation;
    private String suggestedEdit;
    private String confidence;
    private Instant findingCreatedAt;

    // 3. Approved Guideline & Evidence Provenance
    private Long knowledgeEntryId;
    private String ruleCode;
    private String ruleText;
    private String pageOrSectionReference;
    private Long sourceId;
    private String sourceTitle;
    private String sourceType;
    private Integer sourceVersionNumber;
    private String sourceVersionChecksum;
    private String sourceStatus;
    private String evidenceQuote;

    // 4. Creator Response & Revision
    private Long responseId;
    private CreatorAction creatorAction;
    private String creatorReason;
    private String creatorModifiedText;
    private String creatorRespondedBy;
    private Instant creatorRespondedAt;

    // 5. Reviewer Decision
    private Long decisionId;
    private ReviewDecisionType reviewerDecision;
    private String reviewerName;
    private String reviewerComment;
    private String overrideReason;
    private Instant decidedAt;

    // 6. Complete Immutable Audit Trail
    private List<AuditEventDto> auditTrail;
}
