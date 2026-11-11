package com.veriweave.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponseDto {
    private Long runId;
    private String status;
    private int verifiedFindingsCount;
    private int rejectedFindingsCount;
    private int passagesCount;
    private List<FindingDto> findings;
}
