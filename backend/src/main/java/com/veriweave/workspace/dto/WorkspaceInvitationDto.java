package com.veriweave.workspace.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceInvitationDto {

    private Long id;
    private Long workspaceId;
    private String workspaceName;
    private String email;
    private String role;
    private String status;
    private String invitedByEmail;
    private String token;
    private String inviteUrl;
    private Instant expiresAt;
    private Instant createdAt;
}
