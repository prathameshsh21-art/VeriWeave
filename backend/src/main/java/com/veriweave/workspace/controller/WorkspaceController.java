package com.veriweave.workspace.controller;

import com.veriweave.auth.dto.WorkspaceDto;
import com.veriweave.common.ApiResponse;
import com.veriweave.security.UserPrincipal;
import com.veriweave.security.WorkspaceSecurityService;
import com.veriweave.workspace.dto.WorkspaceMemberDto;
import com.veriweave.workspace.entity.Membership;
import com.veriweave.workspace.entity.Role;
import com.veriweave.workspace.entity.Workspace;
import com.veriweave.workspace.repository.MembershipRepository;
import com.veriweave.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceSecurityService workspaceSecurityService;
    private final MembershipRepository membershipRepository;

    @GetMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceDto>> getWorkspace(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Membership membership = workspaceSecurityService.requireMembership(workspaceId, principal);
        Workspace workspace = membership.getWorkspace();

        WorkspaceDto dto = WorkspaceDto.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .slug(workspace.getSlug())
                .role(membership.getRole())
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<WorkspaceDto>> getCurrentWorkspace(
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (workspaceId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("X-Workspace-Id header is required", "BAD_REQUEST"));
        }
        Membership membership = workspaceSecurityService.requireMembership(workspaceId, principal);
        Workspace workspace = membership.getWorkspace();

        WorkspaceDto dto = WorkspaceDto.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .slug(workspace.getSlug())
                .role(membership.getRole())
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<ApiResponse<List<WorkspaceMemberDto>>> getMembers(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurityService.requireMembership(workspaceId, principal, Role.ADMIN);
        List<Membership> memberships = membershipRepository.findAllByWorkspaceIdWithUser(workspaceId);
        List<WorkspaceMemberDto> dtos = memberships.stream()
                .map(m -> WorkspaceMemberDto.builder()
                        .id(m.getId())
                        .userId(m.getUser().getId())
                        .fullName(m.getUser().getFullName())
                        .email(m.getUser().getEmail())
                        .role(m.getRole())
                        .emailVerified(m.getUser().isEmailVerified())
                        .joinedAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }
}
