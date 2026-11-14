package com.veriweave.audit.controller;

import com.veriweave.audit.dto.AuditEventDto;
import com.veriweave.audit.entity.AuditEvent;
import com.veriweave.audit.service.AuditService;
import com.veriweave.common.ApiResponse;
import com.veriweave.security.UserPrincipal;
import com.veriweave.security.WorkspaceSecurityService;
import com.veriweave.workspace.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final WorkspaceSecurityService workspaceSecurity;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditEventDto>>> getWorkspaceAuditTrail(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal);
        List<AuditEvent> events = auditService.getWorkspaceAuditTrail(workspaceId);
        List<AuditEventDto> dtos = events.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    private AuditEventDto toDto(AuditEvent event) {
        AuditEventDto.ActorDto actorDto = null;
        if (event.getActor() != null) {
            actorDto = AuditEventDto.ActorDto.builder()
                    .id(event.getActor().getId())
                    .email(event.getActor().getEmail())
                    .fullName(event.getActor().getFullName())
                    .build();
        }
        return AuditEventDto.builder()
                .id(event.getId())
                .workspaceId(event.getWorkspace() != null ? event.getWorkspace().getId() : null)
                .actor(actorDto)
                .action(event.getAction())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .metadata(event.getMetadata())
                .createdAt(event.getCreatedAt())
                .timestamp(event.getCreatedAt())
                .build();
    }
}
