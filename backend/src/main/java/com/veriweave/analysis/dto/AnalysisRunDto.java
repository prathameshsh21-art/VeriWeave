package com.veriweave.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRunDto {
    private Long id;
    private Long contentVersionId;
    private String modelName;
    private String promptVersion;
    private String status;
    private Integer passagesCount;
    private Integer verifiedFindingsCount;
    private Integer rejectedFindingsCount;
    private Instant completedAt;
    private Instant createdAt;
}
