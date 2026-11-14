package com.veriweave;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veriweave.auth.dto.*;
import com.veriweave.auth.entity.AuthVerificationToken;
import com.veriweave.auth.entity.TokenType;
import com.veriweave.auth.repository.AuthVerificationTokenRepository;
import com.veriweave.workspace.dto.AcceptInvitationRequest;
import com.veriweave.workspace.dto.CreateInvitationRequest;
import com.veriweave.workspace.entity.InvitationStatus;
import com.veriweave.workspace.entity.Role;
import com.veriweave.workspace.entity.User;
import com.veriweave.workspace.entity.WorkspaceInvitation;
import com.veriweave.workspace.repository.UserRepository;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.veriweave.security.RateLimitingService;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TeamInvitationAndAuthHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthVerificationTokenRepository tokenRepository;

    @Autowired
    private RateLimitingService rateLimitingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        rateLimitingService.reset();
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        LoginRequest req = LoginRequest.builder().email(email).password(password).build();
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    private Long getWorkspaceId(String email, String password) throws Exception {
        LoginRequest req = LoginRequest.builder().email(email).password(password).build();
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("activeWorkspace").path("id").asLong();
    }

    @Test
    @DisplayName("Admin can invite team members as CREATOR and REVIEWER, while non-admins are blocked")
    void shouldAllowAdminToInviteMembersAndBlockNonAdmins() throws Exception {
        String adminToken = loginAndGetToken("admin@veriflora.com", "Secret123!");
        String creatorToken = loginAndGetToken("creator@veriflora.com", "Secret123!");
        Long workspaceId = getWorkspaceId("admin@veriflora.com", "Secret123!");

        // 1. Admin invites CREATOR -> 201 Created
        CreateInvitationRequest inviteCreator = CreateInvitationRequest.builder()
                .email("new-writer@veriflora.com")
                .role(Role.CREATOR)
                .build();

        MvcResult creatorInviteRes = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteCreator)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("new-writer@veriflora.com"))
                .andExpect(jsonPath("$.data.role").value("CREATOR"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        // 2. Admin invites REVIEWER -> 201 Created
        CreateInvitationRequest inviteReviewer = CreateInvitationRequest.builder()
                .email("legal-counsel@veriflora.com")
                .role(Role.REVIEWER)
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReviewer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("legal-counsel@veriflora.com"))
                .andExpect(jsonPath("$.data.role").value("REVIEWER"));

        // 3. Creator attempts to invite a member -> 403 Forbidden
        CreateInvitationRequest rogueInvite = CreateInvitationRequest.builder()
                .email("unauthorized-invitee@veriflora.com")
                .role(Role.CREATOR)
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rogueInvite)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin cannot invite an existing workspace member or assign public ADMIN role")
    void shouldPreventDuplicateMembershipAndAdminRoleInvitation() throws Exception {
        String adminToken = loginAndGetToken("admin@veriflora.com", "Secret123!");
        Long workspaceId = getWorkspaceId("admin@veriflora.com", "Secret123!");

        // 1. Attempting to invite an existing member (Marcus Chen / creator@veriflora.com) -> 400 Bad Request
        CreateInvitationRequest dupInvite = CreateInvitationRequest.builder()
                .email("creator@veriflora.com")
                .role(Role.CREATOR)
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupInvite)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already a member")));

        // 2. Attempting to invite as ADMIN -> 400 Bad Request
        CreateInvitationRequest adminInvite = CreateInvitationRequest.builder()
                .email("co-director@veriflora.com")
                .role(Role.ADMIN)
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminInvite)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("only be issued for CREATOR or REVIEWER")));
    }

    @Test
    @DisplayName("Admin can revoke invitation, and revoked invitations cannot be accepted")
    void shouldAllowRevokingInvitations() throws Exception {
        String adminToken = loginAndGetToken("admin@veriflora.com", "Secret123!");
        Long workspaceId = getWorkspaceId("admin@veriflora.com", "Secret123!");

        // 1. Create invitation
        CreateInvitationRequest invite = CreateInvitationRequest.builder()
                .email("revokeme@veriflora.com")
                .role(Role.CREATOR)
                .build();

        MvcResult inviteRes = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invite)))
                .andExpect(status().isCreated())
                .andReturn();

        long inviteId = objectMapper.readTree(inviteRes.getResponse().getContentAsString()).path("data").path("id").asLong();
        String token = objectMapper.readTree(inviteRes.getResponse().getContentAsString()).path("data").path("token").asText();

        // 2. Revoke invitation
        mockMvc.perform(delete("/api/workspaces/" + workspaceId + "/invitations/" + inviteId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 3. Attempting to accept revoked invitation -> 400 Bad Request
        mockMvc.perform(post("/api/auth/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AcceptInvitationRequest.builder().fullName("Test User").password("Secret123!").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("no longer active")));
    }

    @Test
    @DisplayName("Invitee accepts invitation and is enrolled in workspace with specified role")
    void shouldAcceptInvitationSuccessfully() throws Exception {
        String adminToken = loginAndGetToken("admin@veriflora.com", "Secret123!");
        Long workspaceId = getWorkspaceId("admin@veriflora.com", "Secret123!");

        CreateInvitationRequest invite = CreateInvitationRequest.builder()
                .email("accepted-creator@veriflora.com")
                .role(Role.CREATOR)
                .build();

        MvcResult inviteRes = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invite)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(inviteRes.getResponse().getContentAsString()).path("data").path("token").asText();

        // 1. Retrieve invitation details via public endpoint
        mockMvc.perform(get("/api/auth/invitations/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("accepted-creator@veriflora.com"))
                .andExpect(jsonPath("$.data.role").value("CREATOR"));

        // 2. Accept invitation as new user
        AcceptInvitationRequest acceptReq = AcceptInvitationRequest.builder()
                .fullName("Accepted Creator")
                .password("ComplexSecret123!")
                .build();

        MvcResult acceptRes = mockMvc.perform(post("/api/auth/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.activeWorkspace.role").value("CREATOR"))
                .andReturn();
    }

    @Test
    @DisplayName("Password reset invalidates previously issued JWT access tokens")
    void shouldInvalidateTokensOnPasswordReset() throws Exception {
        // Register a distinct user for password reset test
        RegisterRequest registerReq = RegisterRequest.builder()
                .email("resetuser@veriweave.com")
                .password("OldSecret123!")
                .fullName("Reset Target User")
                .workspaceName("Reset Org")
                .build();

        MvcResult regRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String initialAccessToken = objectMapper.readTree(regRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        // Verify initial access token works
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + initialAccessToken))
                .andExpect(status().isOk());

        // Create password reset verification challenge
        String resetCode = "654321";
        AuthVerificationToken token = AuthVerificationToken.builder()
                .email("resetuser@veriweave.com")
                .tokenHash(sha256(resetCode))
                .tokenType(TokenType.PASSWORD_RESET)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .attempts(0)
                .maxAttempts(5)
                .build();
        tokenRepository.save(token);

        // Execute password reset
        ResetPasswordRequest resetReq = ResetPasswordRequest.builder()
                .email("resetuser@veriweave.com")
                .code(resetCode)
                .newPassword("NewSuperSecret123!")
                .build();

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("successfully reset")));

        // Previously issued access token is now rejected because tokenVersion changed!
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + initialAccessToken))
                .andExpect(status().isUnauthorized());

        // Login with new password succeeds and grants a fresh valid token
        String newToken = loginAndGetToken("resetuser@veriweave.com", "NewSuperSecret123!");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Rate limiting rejects abusive authentication attempts with 429 Too Many Requests")
    void shouldTriggerRateLimitOnAbuse() throws Exception {
        LoginRequest req = LoginRequest.builder().email("admin@veriflora.com").password("Secret123!").build();
        String json = objectMapper.writeValueAsString(req);

        // Perform 10 requests (the allowed threshold)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk());
        }

        // 11th request triggers rate limit -> 429 TOO_MANY_REQUESTS
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("Email service status endpoint clearly reports provider readiness")
    void shouldReportEmailServiceStatus() throws Exception {
        mockMvc.perform(get("/api/auth/email-service-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerStatus").isNotEmpty());
    }

    @Test
    @DisplayName("Workspace members endpoint lists all active workspace members for Admin, blocks non-Admins")
    void shouldListWorkspaceMembers() throws Exception {
        String adminToken = loginAndGetToken("admin@veriflora.com", "Secret123!");
        String creatorToken = loginAndGetToken("creator@veriflora.com", "Secret123!");
        String reviewerToken = loginAndGetToken("reviewer@veriflora.com", "Secret123!");
        Long workspaceId = getWorkspaceId("admin@veriflora.com", "Secret123!");

        // Admin can access
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.email == 'admin@veriflora.com')].role").value("ADMIN"))
                .andExpect(jsonPath("$.data[?(@.email == 'creator@veriflora.com')].role").value("CREATOR"))
                .andExpect(jsonPath("$.data[?(@.email == 'reviewer@veriflora.com')].role").value("REVIEWER"));

        // Creator is blocked with 403 Forbidden
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isForbidden());

        // Reviewer is blocked with 403 Forbidden
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Verify AuthVerificationToken and WorkspaceInvitation entity mappings match VARCHAR migration schema")
    void shouldVerifyEntityMappingsMatchV4MigrationSchema() throws Exception {
        // 1. Verify AuthVerificationToken.tokenType mapping
        Field tokenTypeField = AuthVerificationToken.class.getDeclaredField("tokenType");
        Enumerated enumerated = tokenTypeField.getAnnotation(Enumerated.class);
        assertNotNull(enumerated, "tokenType must have @Enumerated");
        assertEquals(EnumType.STRING, enumerated.value(), "tokenType must be EnumType.STRING");

        JdbcTypeCode jdbcTypeCode = tokenTypeField.getAnnotation(JdbcTypeCode.class);
        assertNotNull(jdbcTypeCode, "tokenType must have @JdbcTypeCode(SqlTypes.VARCHAR) for MySQL schema validation");
        assertEquals(SqlTypes.VARCHAR, jdbcTypeCode.value(), "tokenType @JdbcTypeCode must be SqlTypes.VARCHAR");

        Column column = tokenTypeField.getAnnotation(Column.class);
        assertNotNull(column, "tokenType must have @Column");
        assertEquals("token_type", column.name(), "column name must be token_type");
        assertEquals(50, column.length(), "column length must be 50");
        assertFalse(column.nullable(), "token_type column must be NOT NULL");

        // 2. Verify WorkspaceInvitation role and status mappings
        Field roleField = WorkspaceInvitation.class.getDeclaredField("role");
        assertNotNull(roleField.getAnnotation(Enumerated.class));
        assertNotNull(roleField.getAnnotation(JdbcTypeCode.class));
        assertEquals(SqlTypes.VARCHAR, roleField.getAnnotation(JdbcTypeCode.class).value());

        Field statusField = WorkspaceInvitation.class.getDeclaredField("status");
        assertNotNull(statusField.getAnnotation(Enumerated.class));
        assertNotNull(statusField.getAnnotation(JdbcTypeCode.class));
        assertEquals(SqlTypes.VARCHAR, statusField.getAnnotation(JdbcTypeCode.class).value());

        // 3. Verify database persistence and string representation for both TokenType enum values
        AuthVerificationToken emailToken = AuthVerificationToken.builder()
                .email("verify-schema@veriflora.com")
                .tokenHash(sha256("hash1"))
                .tokenType(TokenType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        AuthVerificationToken savedEmailToken = tokenRepository.save(emailToken);

        AuthVerificationToken resetToken = AuthVerificationToken.builder()
                .email("verify-schema@veriflora.com")
                .tokenHash(sha256("hash2"))
                .tokenType(TokenType.PASSWORD_RESET)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        AuthVerificationToken savedResetToken = tokenRepository.save(resetToken);

        // Verify raw SQL queries return VARCHAR string representation matching enum names
        String emailRawType = jdbcTemplate.queryForObject(
                "SELECT token_type FROM auth_verification_tokens WHERE id = ?",
                String.class,
                savedEmailToken.getId()
        );
        assertEquals("EMAIL_VERIFICATION", emailRawType);

        String resetRawType = jdbcTemplate.queryForObject(
                "SELECT token_type FROM auth_verification_tokens WHERE id = ?",
                String.class,
                savedResetToken.getId()
        );
        assertEquals("PASSWORD_RESET", resetRawType);

        // Verify entity retrieval deserializes properly
        AuthVerificationToken fetchedEmail = tokenRepository.findById(savedEmailToken.getId()).orElseThrow();
        assertEquals(TokenType.EMAIL_VERIFICATION, fetchedEmail.getTokenType());

        AuthVerificationToken fetchedReset = tokenRepository.findById(savedResetToken.getId()).orElseThrow();
        assertEquals(TokenType.PASSWORD_RESET, fetchedReset.getTokenType());
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
            throw new RuntimeException(e);
        }
    }
}
