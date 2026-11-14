package com.veriweave.workspace.service;

import com.veriweave.auth.dto.AuthResponse;
import com.veriweave.auth.dto.UserDto;
import com.veriweave.auth.dto.WorkspaceDto;
import com.veriweave.common.email.EmailService;
import com.veriweave.security.JwtService;
import com.veriweave.security.UserPrincipal;
import com.veriweave.security.WorkspaceSecurityService;
import com.veriweave.workspace.dto.AcceptInvitationRequest;
import com.veriweave.workspace.dto.CreateInvitationRequest;
import com.veriweave.workspace.dto.WorkspaceInvitationDto;
import com.veriweave.workspace.entity.*;
import com.veriweave.workspace.repository.MembershipRepository;
import com.veriweave.workspace.repository.UserRepository;
import com.veriweave.workspace.repository.WorkspaceInvitationRepository;
import com.veriweave.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceInvitationService {

    private final WorkspaceInvitationRepository invitationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final WorkspaceSecurityService workspaceSecurity;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${veriweave.frontend.url:https://veriweave-frontend.onrender.com}")
    private String frontendUrl;

    @Transactional
    public WorkspaceInvitationDto createInvitation(Long workspaceId, CreateInvitationRequest request, UserPrincipal principal) {
        // Enforce Server-Side Authorization: Caller must be ADMIN in this workspace
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN);

        if (request.getRole() == null || request.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Team invitations can only be issued for CREATOR or REVIEWER roles.");
        }

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        User inviter = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("Inviter not found"));

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Check if user is already an active member of this workspace
        Optional<User> existingUserOpt = userRepository.findByEmail(normalizedEmail);
        if (existingUserOpt.isPresent()) {
            if (membershipRepository.findByWorkspaceIdAndUserId(workspaceId, existingUserOpt.get().getId()).isPresent()) {
                throw new IllegalArgumentException("User with email " + normalizedEmail + " is already a member of this workspace.");
            }
        }

        // Revoke any existing pending invitations for this email in this workspace
        invitationRepository.findByWorkspaceIdAndEmailAndStatus(workspaceId, normalizedEmail, InvitationStatus.PENDING)
                .ifPresent(existing -> {
                    existing.setStatus(InvitationStatus.REVOKED);
                    invitationRepository.save(existing);
                });

        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .workspace(workspace)
                .email(normalizedEmail)
                .role(request.getRole())
                .token(token)
                .status(InvitationStatus.PENDING)
                .invitedBy(inviter)
                .expiresAt(expiresAt)
                .build();

        invitation = invitationRepository.save(invitation);

        String inviteUrl = frontendUrl + "/invite/" + token;
        emailService.sendWorkspaceInvitation(normalizedEmail, workspace.getName(), request.getRole().name(), inviteUrl);

        log.info("Workspace invitation {} created for {} in workspace {}", invitation.getId(), normalizedEmail, workspace.getName());
        return toDto(invitation);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceInvitationDto> getWorkspaceInvitations(Long workspaceId, UserPrincipal principal) {
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN);
        return invitationRepository.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void revokeInvitation(Long workspaceId, Long invitationId, UserPrincipal principal) {
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN);

        WorkspaceInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found: " + invitationId));

        if (!invitation.getWorkspace().getId().equals(workspaceId)) {
            throw new IllegalArgumentException("Invitation does not belong to the specified workspace.");
        }

        if (invitation.getStatus() == InvitationStatus.PENDING) {
            invitation.setStatus(InvitationStatus.REVOKED);
            invitationRepository.save(invitation);
            log.info("Invitation {} revoked by admin {}", invitationId, principal.getEmail());
        }
    }

    @Transactional(readOnly = true)
    public WorkspaceInvitationDto getInvitationByToken(String token) {
        WorkspaceInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token."));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalArgumentException("Invitation is no longer active (status: " + invitation.getStatus() + ").");
        }

        if (invitation.isExpired()) {
            throw new IllegalArgumentException("Invitation has expired.");
        }

        return toDto(invitation);
    }

    @Transactional
    public AuthResponse acceptInvitation(String token, AcceptInvitationRequest request, UserPrincipal authenticatedPrincipal) {
        WorkspaceInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token."));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalArgumentException("Invitation is no longer active.");
        }

        if (invitation.isExpired()) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new IllegalArgumentException("Invitation has expired.");
        }

        User user;
        if (authenticatedPrincipal != null) {
            user = userRepository.findById(authenticatedPrincipal.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
        } else {
            // New user registration flow via invitation
            String normalizedEmail = invitation.getEmail();
            Optional<User> existingUserOpt = userRepository.findByEmail(normalizedEmail);
            if (existingUserOpt.isPresent()) {
                user = existingUserOpt.get();
                if (request.getPassword() != null && !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                    throw new IllegalArgumentException("Invalid password for existing user " + normalizedEmail);
                }
            } else {
                if (request.getFullName() == null || request.getFullName().trim().isEmpty()) {
                    throw new IllegalArgumentException("Full name is required for account creation.");
                }
                if (request.getPassword() == null || request.getPassword().length() < 8) {
                    throw new IllegalArgumentException("Password must be at least 8 characters long.");
                }

                user = User.builder()
                        .email(normalizedEmail)
                        .fullName(request.getFullName().trim())
                        .passwordHash(passwordEncoder.encode(request.getPassword()))
                        .emailVerified(true) // Invitation email token proves ownership
                        .tokenVersion(1)
                        .build();
                user = userRepository.save(user);
            }
        }

        // Add membership
        Workspace workspace = invitation.getWorkspace();
        if (membershipRepository.findByWorkspaceIdAndUserId(workspace.getId(), user.getId()).isEmpty()) {
            Membership membership = Membership.builder()
                    .workspace(workspace)
                    .user(user)
                    .role(invitation.getRole())
                    .build();
            membershipRepository.save(membership);
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitationRepository.save(invitation);

        List<Membership> memberships = membershipRepository.findAllByUserIdWithWorkspace(user.getId());
        List<WorkspaceDto> workspaceDtos = memberships.stream()
                .map(m -> WorkspaceDto.builder()
                        .id(m.getWorkspace().getId())
                        .name(m.getWorkspace().getName())
                        .slug(m.getWorkspace().getSlug())
                        .role(m.getRole())
                        .build())
                .collect(Collectors.toList());

        WorkspaceDto activeWorkspace = WorkspaceDto.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .slug(workspace.getSlug())
                .role(invitation.getRole())
                .build();

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getFullName(), user.getTokenVersion());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail(), user.getTokenVersion());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(UserDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .build())
                .memberships(workspaceDtos)
                .activeWorkspace(activeWorkspace)
                .build();
    }

    private WorkspaceInvitationDto toDto(WorkspaceInvitation invitation) {
        return WorkspaceInvitationDto.builder()
                .id(invitation.getId())
                .workspaceId(invitation.getWorkspace().getId())
                .workspaceName(invitation.getWorkspace().getName())
                .email(invitation.getEmail())
                .role(invitation.getRole().name())
                .status(invitation.getStatus().name())
                .invitedByEmail(invitation.getInvitedBy().getEmail())
                .token(invitation.getToken())
                .inviteUrl(frontendUrl + "/invite/" + invitation.getToken())
                .expiresAt(invitation.getExpiresAt())
                .createdAt(invitation.getCreatedAt())
                .build();
    }
}
