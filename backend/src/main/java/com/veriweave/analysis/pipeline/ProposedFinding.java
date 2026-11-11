package com.veriweave.analysis.pipeline;

import com.veriweave.analysis.entity.FindingType;
import com.veriweave.knowledge.entity.EntryCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProposedFinding {
    private Integer passageStart;
    private Integer passageEnd;
    private String passageQuote;
    private Long knowledgeEntryId;
    private String evidenceQuote;
    private EntryCategory category;
    private FindingType type;
    private String explanation;
    private String suggestedEdit;
    @Builder.Default
    private String confidence = "HIGH";
    private boolean isAdvisoryOnly;
}
