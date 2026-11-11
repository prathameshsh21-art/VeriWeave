package com.veriweave.knowledge.dto;

import com.veriweave.knowledge.entity.EntryCategory;
import com.veriweave.knowledge.entity.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeEntryDto {
    private Long id;
    private Long sourceVersionId;
    private String code;
    private EntryCategory category;
    private Severity defaultSeverity;
    private String text;
    private String pageOrSectionReference;
}
