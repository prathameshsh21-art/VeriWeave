package com.veriweave.auth.service;

import com.veriweave.auth.dto.*;
import com.veriweave.security.JwtService;
import com.veriweave.security.UserPrincipal;
import com.veriweave.workspace.entity.Membership;
import com.veriweave.workspace.entity.Role;
import com.veriweave.workspace.entity.User;
import com.veriweave.workspace.entity.Workspace;
import com.veriweave.workspace.repository.MembershipRepository;
import com.veriweave.workspace.repository.UserRepository;
import com.veriweave.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email already registered: " + normalizedEmail);
        }

        User user = User.builder()
                .email(normalizedEmail)
                .fullName(request.getFullName().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        user = userRepository.save(user);

        // First user creates their workspace and becomes ADMIN (Proposal §5 step 1)
        String workspaceName = (request.getWorkspaceName() != null && !request.getWorkspaceName().isBlank())
                ? request.getWorkspaceName().trim()
                : user.getFullName() + "'s Brand";

        String baseSlug = workspaceName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (baseSlug.isBlank()) {
            baseSlug = "workspace";
        }
        String slug = baseSlug;
        if (workspaceRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + UUID.randomUUID().toString().substring(0, 6);
        }

        Workspace workspace = Workspace.builder()
                .name(workspaceName)
                .slug(slug)
                .build();
        workspace = workspaceRepository.save(workspace);

        Membership membership = Membership.builder()
                .workspace(workspace)
                .user(user)
                .role(Role.ADMIN)
                .build();
        membership = membershipRepository.save(membership);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getFullName());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        WorkspaceDto workspaceDto = toWorkspaceDto(membership);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserDto(user))
                .memberships(List.of(workspaceDto))
                .activeWorkspace(workspaceDto)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
        );

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Membership> memberships = membershipRepository.findAllByUserIdWithWorkspace(user.getId());
        List<WorkspaceDto> workspaceDtos = memberships.stream()
                .map(this::toWorkspaceDto)
                .collect(Collectors.toList());

        WorkspaceDto activeWorkspace = workspaceDtos.isEmpty() ? null : workspaceDtos.get(0);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getFullName());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserDto(user))
                .memberships(workspaceDtos)
                .activeWorkspace(activeWorkspace)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();
        if (!jwtService.isRefreshToken(token)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        String email = jwtService.extractUsername(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Membership> memberships = membershipRepository.findAllByUserIdWithWorkspace(user.getId());
        List<WorkspaceDto> workspaceDtos = memberships.stream()
                .map(this::toWorkspaceDto)
                .collect(Collectors.toList());

        WorkspaceDto activeWorkspace = workspaceDtos.isEmpty() ? null : workspaceDtos.get(0);

        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getFullName());
        String newRefreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .user(toUserDto(user))
                .memberships(workspaceDtos)
                .activeWorkspace(activeWorkspace)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse getCurrentUser(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Membership> memberships = membershipRepository.findAllByUserIdWithWorkspace(user.getId());
        List<WorkspaceDto> workspaceDtos = memberships.stream()
                .map(this::toWorkspaceDto)
                .collect(Collectors.toList());

        WorkspaceDto activeWorkspace = workspaceDtos.isEmpty() ? null : workspaceDtos.get(0);

        return AuthResponse.builder()
                .user(toUserDto(user))
                .memberships(workspaceDtos)
                .activeWorkspace(activeWorkspace)
                .build();
    }

    private UserDto toUserDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    private WorkspaceDto toWorkspaceDto(Membership membership) {
        return WorkspaceDto.builder()
                .id(membership.getWorkspace().getId())
                .name(membership.getWorkspace().getName())
                .slug(membership.getWorkspace().getSlug())
                .role(membership.getRole())
                .build();
    }
}
