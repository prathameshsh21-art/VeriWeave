package com.veriweave.submission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentVersionDto {
    private Long id;
    private Long submissionId;
    private Integer versionNumber;
    private String rawText;
    private String checksum;
    private String createdBy;
    private Instant createdAt;
}
