package com.veriweave.knowledge.dto;

import com.veriweave.knowledge.entity.SourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSourceDto {
    private Long id;
    private Long workspaceId;
    private String title;
    private SourceType type;
    private boolean archived;
    private SourceVersionDto currentVersion;
    private Instant createdAt;
    private Instant updatedAt;
}
