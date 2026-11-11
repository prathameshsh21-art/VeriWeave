package com.veriweave.workspace.controller;

import com.veriweave.auth.dto.WorkspaceDto;
import com.veriweave.common.ApiResponse;
import com.veriweave.security.UserPrincipal;
import com.veriweave.security.WorkspaceSecurityService;
import com.veriweave.workspace.entity.Membership;
import com.veriweave.workspace.entity.Workspace;
import com.veriweave.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceSecurityService workspaceSecurityService;

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
}
