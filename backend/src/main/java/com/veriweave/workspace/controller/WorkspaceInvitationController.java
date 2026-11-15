package com.veriweave.workspace.controller;

import com.veriweave.auth.dto.AuthResponse;
import com.veriweave.common.ApiResponse;
import com.veriweave.security.RateLimitingService;
import com.veriweave.security.UserPrincipal;
import com.veriweave.workspace.dto.AcceptInvitationRequest;
import com.veriweave.workspace.dto.CreateInvitationRequest;
import com.veriweave.workspace.dto.WorkspaceInvitationDto;
import com.veriweave.workspace.service.WorkspaceInvitationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class WorkspaceInvitationController {

    private final WorkspaceInvitationService invitationService;
    private final RateLimitingService rateLimitingService;

    @PostMapping("/api/workspaces/{workspaceId}/invitations")
    public ResponseEntity<ApiResponse<WorkspaceInvitationDto>> inviteMember(
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateInvitationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        rateLimitingService.checkRateLimit("ws_invite:" + workspaceId, 15, 3600); // 15 per hour
        WorkspaceInvitationDto dto = invitationService.createInvitation(workspaceId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(dto));
    }

    @GetMapping("/api/workspaces/{workspaceId}/invitations")
    public ResponseEntity<ApiResponse<List<WorkspaceInvitationDto>>> getInvitations(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<WorkspaceInvitationDto> dtos = invitationService.getWorkspaceInvitations(workspaceId, principal);
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @DeleteMapping("/api/workspaces/{workspaceId}/invitations/{invitationId}")
    public ResponseEntity<ApiResponse<Void>> revokeInvitation(
            @PathVariable Long workspaceId,
            @PathVariable Long invitationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        invitationService.revokeInvitation(workspaceId, invitationId, principal);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/api/workspaces/{workspaceId}/invitations/{invitationId}/resend")
    public ResponseEntity<ApiResponse<WorkspaceInvitationDto>> resendInvitation(
            @PathVariable Long workspaceId,
            @PathVariable Long invitationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        rateLimitingService.checkRateLimit("ws_resend_invite:" + workspaceId, 15, 3600);
        WorkspaceInvitationDto dto = invitationService.resendInvitation(workspaceId, invitationId, principal);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/api/auth/invitations/{token}")
    public ResponseEntity<ApiResponse<WorkspaceInvitationDto>> getInvitationByToken(
            @PathVariable String token
    ) {
        WorkspaceInvitationDto dto = invitationService.getInvitationByToken(token);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping("/api/auth/invitations/{token}/accept")
    public ResponseEntity<ApiResponse<AuthResponse>> acceptInvitation(
            @PathVariable String token,
            @RequestBody(required = false) AcceptInvitationRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest.getRemoteAddr();
        rateLimitingService.checkRateLimit("invite_accept:" + clientIp, 10, 600); // 10 per 10 mins

        AcceptInvitationRequest req = request != null ? request : new AcceptInvitationRequest();
        AuthResponse response = invitationService.acceptInvitation(token, req, principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
