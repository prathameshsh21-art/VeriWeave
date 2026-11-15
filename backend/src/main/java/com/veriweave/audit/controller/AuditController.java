package com.veriweave.audit.controller;

import com.veriweave.analysis.repository.FindingRepository;
import com.veriweave.audit.dto.AuditEventDto;
import com.veriweave.audit.entity.AuditEvent;
import com.veriweave.audit.service.AuditService;
import com.veriweave.common.ApiResponse;
import com.veriweave.security.UserPrincipal;
import com.veriweave.security.WorkspaceSecurityService;
import com.veriweave.submission.entity.Submission;
import com.veriweave.submission.repository.SubmissionRepository;
import com.veriweave.workspace.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final WorkspaceSecurityService workspaceSecurity;
    private final SubmissionRepository submissionRepository;
    private final FindingRepository findingRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditEventDto>>> getWorkspaceAuditTrail(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        var membership = workspaceSecurity.requireMembership(workspaceId, principal);
        List<AuditEvent> events = auditService.getWorkspaceAuditTrail(workspaceId);

        if (membership.getRole() == Role.CREATOR) {
            Set<Long> creatorSubmissionIds = submissionRepository
                    .findAllByWorkspaceIdAndCreatedById(workspaceId, principal.getId())
                    .stream()
                    .map(Submission::getId)
                    .collect(Collectors.toSet());

            events = events.stream()
                    .filter(e -> (e.getActor() != null && e.getActor().getId().equals(principal.getId()))
                            || ("Submission".equalsIgnoreCase(e.getEntityType()) && creatorSubmissionIds.contains(e.getEntityId()))
                            || ("Finding".equalsIgnoreCase(e.getEntityType()) && isFindingOfCreatorSubmission(e.getEntityId(), creatorSubmissionIds)))
                    .collect(Collectors.toList());
        }

        List<AuditEventDto> dtos = events.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    private boolean isFindingOfCreatorSubmission(Long findingId, Set<Long> creatorSubmissionIds) {
        if (findingId == null || creatorSubmissionIds.isEmpty()) return false;
        return findingRepository.findById(findingId)
                .map(f -> creatorSubmissionIds.contains(f.getContentVersion().getSubmission().getId()))
                .orElse(false);
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
