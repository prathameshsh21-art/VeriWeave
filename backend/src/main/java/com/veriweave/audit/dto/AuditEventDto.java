package com.veriweave.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventDto {
    private Long id;
    private Long workspaceId;
    private ActorDto actor;
    private String action;
    private String entityType;
    private Long entityId;
    private String metadata;
    private Instant createdAt;
    private Instant timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActorDto {
        private Long id;
        private String email;
        private String fullName;
    }
}
