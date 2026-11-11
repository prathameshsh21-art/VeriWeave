package com.veriweave.analysis.dto;

import com.veriweave.analysis.entity.FindingClassification;
import com.veriweave.analysis.entity.FindingType;
import com.veriweave.knowledge.entity.EntryCategory;
import com.veriweave.knowledge.entity.Severity;
import com.veriweave.review.dto.FindingResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindingDto {
    private Long id;
    private Long contentVersionId;
    private Long knowledgeEntryId;
    private String ruleCode;
    private String sourceTitle;
    private Integer sourceVersionNumber;
    private FindingType type;
    private EntryCategory category;
    private FindingClassification classification;
    private Severity severity;
    private Integer passageStart;
    private Integer passageEnd;
    private String passageQuote;
    private String evidenceQuote;
    private String explanation;
    private String suggestedEdit;
    private String confidence;
    private FindingResponseDto response;
    private Instant createdAt;
}
