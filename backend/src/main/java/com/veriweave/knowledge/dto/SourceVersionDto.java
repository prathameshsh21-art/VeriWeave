package com.veriweave.knowledge.dto;

import com.veriweave.auth.dto.UserDto;
import com.veriweave.knowledge.entity.VersionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceVersionDto {
    private Long id;
    private Long sourceId;
    private Integer versionNumber;
    private VersionStatus status;
    private String checksum;
    private String fileReference;
    private UserDto approvedBy;
    private Instant approvedAt;
    private Instant effectiveFrom;
    private Instant effectiveTo;
    private String changelogNotes;
    private long entryCount;
}
