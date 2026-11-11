package com.veriweave.submission.dto;

import com.veriweave.submission.entity.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionDto {
    private Long id;
    private Long workspaceId;
    private String title;
    private SubmissionStatus status;
    private String createdBy;
    private String createdByEmail;
    private ContentVersionDto currentVersion;
    private Instant createdAt;
    private Instant updatedAt;
}
