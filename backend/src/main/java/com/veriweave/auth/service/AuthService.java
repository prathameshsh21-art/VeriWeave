package com.veriweave.auth.service;

import com.veriweave.auth.dto.*;
import com.veriweave.auth.entity.AuthVerificationToken;
import com.veriweave.auth.entity.TokenType;
import com.veriweave.auth.repository.AuthVerificationTokenRepository;
import com.veriweave.common.email.EmailService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final MembershipRepository membershipRepository;
    private final AuthVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email already registered: " + normalizedEmail);
        }

        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }

        boolean requireVerification = emailService.isConfigured();

        User user = User.builder()
                .email(normalizedEmail)
                .fullName(request.getFullName().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .emailVerified(!requireVerification) // If SMTP is configured, require verification before login
                .tokenVersion(1)
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

        if (requireVerification) {
            // Issue 6-digit email verification challenge
            String code = String.format("%06d", new SecureRandom().nextInt(1000000));
            createAndSendVerificationToken(normalizedEmail, code, TokenType.EMAIL_VERIFICATION);
            log.info("New registration for {} created (pending email verification)", normalizedEmail);

            WorkspaceDto workspaceDto = toWorkspaceDto(membership);
            return AuthResponse.builder()
                    .user(toUserDto(user))
                    .memberships(List.of(workspaceDto))
                    .activeWorkspace(workspaceDto)
                    .build();
        }

        log.info("New registration for {} created and auto-activated (SMTP unconfigured or dev environment)", normalizedEmail);
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getFullName(), user.getTokenVersion());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail(), user.getTokenVersion());

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

        if (!user.isEmailVerified()) {
            throw new IllegalArgumentException("Account email is not verified. Please verify your email before logging in.");
        }

        List<Membership> memberships = membershipRepository.findAllByUserIdWithWorkspace(user.getId());
        List<WorkspaceDto> workspaceDtos = memberships.stream()
                .map(this::toWorkspaceDto)
                .collect(Collectors.toList());

        WorkspaceDto activeWorkspace = workspaceDtos.isEmpty() ? null : workspaceDtos.get(0);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getFullName(), user.getTokenVersion());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail(), user.getTokenVersion());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserDto(user))
                .memberships(workspaceDtos)
                .activeWorkspace(activeWorkspace)
                .build();
    }

    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.isEmailVerified()) {
            return generateSessionForUser(user);
        }

        AuthVerificationToken token = tokenRepository
                .findFirstByEmailAndTokenTypeAndUsedAtIsNullOrderByCreatedAtDesc(normalizedEmail, TokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new IllegalArgumentException("No active verification code found for this email."));

        if (token.isExpired()) {
            throw new IllegalArgumentException("Verification code has expired. Please request a new one.");
        }

        if (token.isAttemptsExceeded()) {
            throw new IllegalArgumentException("Maximum verification attempts exceeded. Please request a new code.");
        }

        token.setAttempts(token.getAttempts() + 1);

        String inputHash = sha256(request.getCode().trim());
        if (!MessageDigest.isEqual(inputHash.getBytes(StandardCharsets.UTF_8), token.getTokenHash().getBytes(StandardCharsets.UTF_8))) {
            tokenRepository.save(token);
            throw new IllegalArgumentException("Invalid verification code. Remaining attempts: " + (token.getMaxAttempts() - token.getAttempts()));
        }

        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        user.setEmailVerified(true);
        user = userRepository.save(user);

        log.info("Email verified successfully for user {}", normalizedEmail);
        return generateSessionForUser(user);
    }

    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("Email is already verified.");
        }

        Optional<AuthVerificationToken> existing = tokenRepository
                .findFirstByEmailAndTokenTypeAndUsedAtIsNullOrderByCreatedAtDesc(normalizedEmail, TokenType.EMAIL_VERIFICATION);

        if (existing.isPresent()) {
            long secondsSinceLast = ChronoUnit.SECONDS.between(existing.get().getCreatedAt(), Instant.now());
            if (secondsSinceLast < 60) {
                throw new IllegalArgumentException("Please wait " + (60 - secondsSinceLast) + " seconds before requesting another code.");
            }
            existing.get().setUsedAt(Instant.now());
            tokenRepository.save(existing.get());
        }

        String code = String.format("%06d", new SecureRandom().nextInt(1000000));
        createAndSendVerificationToken(normalizedEmail, code, TokenType.EMAIL_VERIFICATION);
        log.info("Resent verification code for {}", normalizedEmail);
    }

    @Transactional
    public void changeEmail(ChangeEmailRequest request) {
        String currentEmail = request.getCurrentEmail().trim().toLowerCase();
        String newEmail = request.getNewEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new IllegalArgumentException("Current user not found"));

        if (user.isEmailVerified()) {
            throw new IllegalStateException("Cannot change email of an already verified account.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid password.");
        }

        if (userRepository.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("Email is already in use: " + newEmail);
        }

        // Invalidate old tokens
        tokenRepository.findAllByEmailAndTokenTypeOrderByCreatedAtDesc(currentEmail, TokenType.EMAIL_VERIFICATION)
                .forEach(t -> {
                    t.setUsedAt(Instant.now());
                    tokenRepository.save(t);
                });

        user.setEmail(newEmail);
        userRepository.save(user);

        String code = String.format("%06d", new SecureRandom().nextInt(1000000));
        createAndSendVerificationToken(newEmail, code, TokenType.EMAIL_VERIFICATION);
        log.info("Email changed from {} to {} before verification", currentEmail, newEmail);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

        // Security best practice: Always return generic acceptance message regardless of whether user exists
        if (userOpt.isEmpty() || !userOpt.get().isEmailVerified()) {
            log.info("Password reset requested for nonexistent or unverified email: {}", normalizedEmail);
            return;
        }

        // Invalidate previous reset tokens
        tokenRepository.findAllByEmailAndTokenTypeOrderByCreatedAtDesc(normalizedEmail, TokenType.PASSWORD_RESET)
                .forEach(t -> {
                    t.setUsedAt(Instant.now());
                    tokenRepository.save(t);
                });

        String code = String.format("%06d", new SecureRandom().nextInt(1000000));
        createAndSendVerificationToken(normalizedEmail, code, TokenType.PASSWORD_RESET);
        log.info("Password reset code generated and sent for {}", normalizedEmail);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (request.getNewPassword() == null || !PASSWORD_PATTERN.matcher(request.getNewPassword()).matches()) {
            throw new IllegalArgumentException("Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, and one number.");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        AuthVerificationToken token = tokenRepository
                .findFirstByEmailAndTokenTypeAndUsedAtIsNullOrderByCreatedAtDesc(normalizedEmail, TokenType.PASSWORD_RESET)
                .orElseThrow(() -> new IllegalArgumentException("No active password reset code found for this email."));

        if (token.isExpired()) {
            throw new IllegalArgumentException("Password reset code has expired. Please request a new one.");
        }

        if (token.isAttemptsExceeded()) {
            throw new IllegalArgumentException("Maximum attempts exceeded. Please request a new code.");
        }

        token.setAttempts(token.getAttempts() + 1);

        String inputHash = sha256(request.getCode().trim());
        if (!MessageDigest.isEqual(inputHash.getBytes(StandardCharsets.UTF_8), token.getTokenHash().getBytes(StandardCharsets.UTF_8))) {
            tokenRepository.save(token);
            throw new IllegalArgumentException("Invalid verification code. Remaining attempts: " + (token.getMaxAttempts() - token.getAttempts()));
        }

        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        // Update password and increment token version to revoke all existing sessions
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        log.info("Password successfully reset and active sessions revoked for {}", normalizedEmail);
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

        Integer tokenVersion = jwtService.extractTokenVersion(token);
        if (tokenVersion != null && tokenVersion != user.getTokenVersion()) {
            throw new IllegalArgumentException("Refresh token has been revoked due to password change or session reset.");
        }

        List<Membership> memberships = membershipRepository.findAllByUserIdWithWorkspace(user.getId());
        List<WorkspaceDto> workspaceDtos = memberships.stream()
                .map(this::toWorkspaceDto)
                .collect(Collectors.toList());

        WorkspaceDto activeWorkspace = workspaceDtos.isEmpty() ? null : workspaceDtos.get(0);

        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getFullName(), user.getTokenVersion());
        String newRefreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail(), user.getTokenVersion());

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

    private AuthResponse generateSessionForUser(User user) {
        List<Membership> memberships = membershipRepository.findAllByUserIdWithWorkspace(user.getId());
        List<WorkspaceDto> workspaceDtos = memberships.stream()
                .map(this::toWorkspaceDto)
                .collect(Collectors.toList());

        WorkspaceDto activeWorkspace = workspaceDtos.isEmpty() ? null : workspaceDtos.get(0);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getFullName(), user.getTokenVersion());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail(), user.getTokenVersion());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserDto(user))
                .memberships(workspaceDtos)
                .activeWorkspace(activeWorkspace)
                .build();
    }

    private void createAndSendVerificationToken(String email, String code, TokenType tokenType) {
        AuthVerificationToken verificationToken = AuthVerificationToken.builder()
                .email(email)
                .tokenHash(sha256(code))
                .tokenType(tokenType)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .attempts(0)
                .maxAttempts(5)
                .build();
        tokenRepository.save(verificationToken);

        if (tokenType == TokenType.EMAIL_VERIFICATION) {
            emailService.sendVerificationCode(email, code);
        } else if (tokenType == TokenType.PASSWORD_RESET) {
            emailService.sendPasswordResetCode(email, code);
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
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
