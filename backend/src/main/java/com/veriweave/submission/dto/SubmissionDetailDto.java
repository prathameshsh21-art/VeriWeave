package com.veriweave.submission.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.veriweave.analysis.dto.AnalysisRunDto;
import com.veriweave.analysis.dto.FindingDto;
import com.veriweave.review.dto.FindingResponseDto;
import com.veriweave.submission.entity.SubmissionStatus;
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
public class SubmissionDetailDto {
    private Long id;
    private Long workspaceId;
    private String title;
    private String campaignName;
    private SubmissionStatus status;
    private CreatorDto createdBy;
    private ContentVersionDto currentVersion;
    private ContentVersionDto activeVersion;
    private Integer currentVersionNumber;
    private List<FindingDto> findings;
    private List<FindingResponseDto> responses;
    private AnalysisRunDto latestAnalysisRun;
    private Instant createdAt;
    private Instant updatedAt;

    @JsonProperty("submission")
    public SubmissionSummary getSubmission() {
        return SubmissionSummary.builder()
                .id(id)
                .workspaceId(workspaceId)
                .title(title)
                .campaignName(campaignName)
                .status(status)
                .createdBy(createdBy)
                .currentVersionNumber(currentVersionNumber)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatorDto {
        private Long id;
        private String email;
        private String fullName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionSummary {
        private Long id;
        private Long workspaceId;
        private String title;
        private String campaignName;
        private SubmissionStatus status;
        private CreatorDto createdBy;
        private Integer currentVersionNumber;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
