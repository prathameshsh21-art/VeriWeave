package com.veriweave;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veriweave.common.email.EmailDeliveryException;
import com.veriweave.common.email.EmailDeliveryResult;
import com.veriweave.common.email.EmailService;
import com.veriweave.security.RateLimitingService;
import com.veriweave.workspace.dto.AcceptInvitationRequest;
import com.veriweave.workspace.dto.CreateInvitationRequest;
import com.veriweave.workspace.dto.WorkspaceInvitationDto;
import com.veriweave.workspace.entity.InvitationStatus;
import com.veriweave.workspace.entity.Role;
import com.veriweave.workspace.entity.WorkspaceInvitation;
import com.veriweave.workspace.repository.MembershipRepository;
import com.veriweave.workspace.repository.WorkspaceInvitationRepository;
import com.veriweave.workspace.service.WorkspaceInvitationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class EmailDeliveryAndRoutingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmailService emailService;

    @Autowired
    private WorkspaceInvitationService invitationService;

    @Autowired
    private WorkspaceInvitationRepository invitationRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private RateLimitingService rateLimitingService;

    @MockBean
    private JavaMailSender mockMailSender;

    @BeforeEach
    void setUp() {
        rateLimitingService.reset();
        reset(mockMailSender);
        ReflectionTestUtils.setField(emailService, "mailHost", "");
        ReflectionTestUtils.setField(emailService, "mailPort", 587);
        ReflectionTestUtils.setField(emailService, "mailSender", mockMailSender);
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        var req = java.util.Map.of("email", email, "password", password);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    private Long getWorkspaceId(String email, String password) throws Exception {
        var req = java.util.Map.of("email", email, "password", password);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("activeWorkspace").path("id").asLong();
    }

    @Test
    @DisplayName("Password reset email delivery dispatches correctly when SMTP is configured and uses VERIWEAVE_MAIL_FROM")
    void shouldDispatchPasswordResetEmailSuccessfully() {
        ReflectionTestUtils.setField(emailService, "mailHost", "smtp.sendgrid.net");
        ReflectionTestUtils.setField(emailService, "mailFrom", "notifications@veriweave.com");
        assertTrue(emailService.isConfigured());

        emailService.sendPasswordResetCode("recipient@example.com", "492019");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mockMailSender, times(1)).send(captor.capture());
        assertEquals("notifications@veriweave.com", captor.getValue().getFrom());
        assertArrayEquals(new String[]{"recipient@example.com"}, captor.getValue().getTo());
        assertTrue(captor.getValue().getSubject().contains("Password Reset"));
        assertTrue(captor.getValue().getText().contains("492019"));
    }

    @Test
    @DisplayName("Password reset email delivery throws EmailDeliveryException when transport fails")
    void shouldHandlePasswordResetEmailTransportFailure() {
        ReflectionTestUtils.setField(emailService, "mailHost", "smtp.sendgrid.net");
        doThrow(new MailSendException("SMTP connection timed out"))
                .when(mockMailSender).send(any(SimpleMailMessage.class));

        EmailDeliveryException ex = assertThrows(EmailDeliveryException.class, () ->
                emailService.sendPasswordResetCode("fail@example.com", "123456"));
        assertTrue(ex.getMessage().contains("SMTP transport failure"));
    }

    @Test
    @DisplayName("Email verification delivery dispatches 6-digit code when SMTP is configured")
    void shouldDispatchEmailVerificationCode() {
        ReflectionTestUtils.setField(emailService, "mailHost", "smtp.mailgun.org");
        assertTrue(emailService.isConfigured());

        emailService.sendVerificationCode("newuser@example.com", "882194");

        verify(mockMailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("SMTP-unavailable handling: returns honest failure and clear diagnostics without crashing")
    void shouldHandleSmtpUnavailableGracefully() {
        ReflectionTestUtils.setField(emailService, "mailHost", "");
        assertFalse(emailService.isConfigured());

        // Password reset throws clear EmailDeliveryException
        EmailDeliveryException pwEx = assertThrows(EmailDeliveryException.class, () ->
                emailService.sendPasswordResetCode("someone@example.com", "123456"));
        assertTrue(pwEx.getMessage().contains("missing SPRING_MAIL_HOST"));

        // Email verification throws clear EmailDeliveryException
        EmailDeliveryException verifyEx = assertThrows(EmailDeliveryException.class, () ->
                emailService.sendVerificationCode("someone@example.com", "123456"));
        assertTrue(verifyEx.getMessage().contains("missing SPRING_MAIL_HOST"));

        // Workspace invitation returns honest delivery result
        EmailDeliveryResult inviteRes = emailService.sendWorkspaceInvitation(
                "colleague@example.com",
                "VeriFlora Botanicals",
                "CREATOR",
                "https://veriweave-frontend.onrender.com/invite/tok123"
        );
        assertFalse(inviteRes.isDelivered());
        assertTrue(inviteRes.getMessage().contains("unconfigured"));
    }

    @Test
    @DisplayName("Invitation URL generation normalizes base URL and prevents trailing slash issues")
    void shouldGenerateInvitationUrlWithoutTrailingSlashDuplication() {
        ReflectionTestUtils.setField(invitationService, "frontendUrl", "https://veriweave-frontend.onrender.com/");
        assertEquals("https://veriweave-frontend.onrender.com", invitationService.getNormalizedFrontendUrl());

        ReflectionTestUtils.setField(invitationService, "frontendUrl", "https://veriweave.xyz///");
        assertEquals("https://veriweave.xyz", invitationService.getNormalizedFrontendUrl());

        ReflectionTestUtils.setField(invitationService, "frontendUrl", "");
        assertEquals("https://veriweave-frontend.onrender.com", invitationService.getNormalizedFrontendUrl());
    }

    @Test
    @DisplayName("Invitation creation allows safe retry without creating duplicate active invitations")
    void shouldPreventDuplicateActiveInvitationsOnRetry() throws Exception {
        String adminToken = loginAndGetToken("admin@veriflora.com", "Secret123!");
        Long workspaceId = getWorkspaceId("admin@veriflora.com", "Secret123!");

        CreateInvitationRequest req1 = CreateInvitationRequest.builder()
                .email("idempotent-invite@veriflora.com")
                .role(Role.CREATOR)
                .build();

        // First invitation creation
        MvcResult res1 = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("idempotent-invite@veriflora.com"))
                .andReturn();

        String token1 = objectMapper.readTree(res1.getResponse().getContentAsString()).path("data").path("token").asText();

        // Second invitation creation for same email (retry / role update)
        CreateInvitationRequest req2 = CreateInvitationRequest.builder()
                .email("idempotent-invite@veriflora.com")
                .role(Role.REVIEWER)
                .build();

        MvcResult res2 = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("REVIEWER"))
                .andReturn();

        // Confirm exactly 1 active PENDING invitation exists for this email
        var pendingInvites = invitationRepository.findAllByWorkspaceIdAndStatus(workspaceId, InvitationStatus.PENDING)
                .stream()
                .filter(inv -> inv.getEmail().equals("idempotent-invite@veriflora.com"))
                .toList();
        assertEquals(1, pendingInvites.size(), "Only one active PENDING invitation must exist for the email");
        Long invitationId = pendingInvites.get(0).getId();

        // 3. Admin can resend invitation -> 200 OK
        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/invitations/" + invitationId + "/resend")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(invitationId))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // 4. Non-admin (CREATOR) attempting to resend invitation is blocked -> 403 Forbidden
        String creatorToken = loginAndGetToken("creator@veriflora.com", "Secret123!");
        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/invitations/" + invitationId + "/resend")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Expired, invalid and already-used invitation tokens are rejected with appropriate error")
    void shouldRejectInvalidExpiredAndUsedInvitationTokens() throws Exception {
        // 1. Non-existent token -> 400 Bad Request
        mockMvc.perform(get("/api/auth/invitations/completely-invalid-token-xyz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid invitation token")));

        // 2. Expired token -> 400 Bad Request
        String adminToken = loginAndGetToken("admin@veriflora.com", "Secret123!");
        Long workspaceId = getWorkspaceId("admin@veriflora.com", "Secret123!");

        CreateInvitationRequest req = CreateInvitationRequest.builder()
                .email("expired-check@veriflora.com")
                .role(Role.CREATOR)
                .build();

        MvcResult createRes = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("data").path("token").asText();

        // Manually expire the invitation
        WorkspaceInvitation invitation = invitationRepository.findByToken(token).orElseThrow();
        invitation.setExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS));
        invitationRepository.save(invitation);

        mockMvc.perform(get("/api/auth/invitations/" + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("expired")));
    }

    @Test
    @DisplayName("Invitation acceptance creates user, assigns workspace role and marks token ACCEPTED")
    void shouldAcceptInvitationAndGrantWorkspaceAccess() throws Exception {
        String adminToken = loginAndGetToken("admin@veriflora.com", "Secret123!");
        Long workspaceId = getWorkspaceId("admin@veriflora.com", "Secret123!");

        CreateInvitationRequest req = CreateInvitationRequest.builder()
                .email("accept-tester@veriflora.com")
                .role(Role.REVIEWER)
                .build();

        MvcResult createRes = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("data").path("token").asText();

        // Accept invitation
        AcceptInvitationRequest acceptReq = AcceptInvitationRequest.builder()
                .fullName("Acceptance Reviewer")
                .password("StrongPass123!")
                .build();

        MvcResult acceptRes = mockMvc.perform(post("/api/auth/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeWorkspace.role").value("REVIEWER"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        // Token must now be marked ACCEPTED
        WorkspaceInvitation updated = invitationRepository.findByToken(token).orElseThrow();
        assertEquals(InvitationStatus.ACCEPTED, updated.getStatus());

        // Attempting to reuse already-accepted token -> 400 Bad Request
        mockMvc.perform(post("/api/auth/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("no longer active")));
    }

    @Test
    @DisplayName("CORS allows configured production origins (Render and custom domains) and rejects unauthorized origins")
    void shouldEnforceCorsForProductionDomains() throws Exception {
        // Allowed origins
        String[] allowed = {
                "https://veriweave-frontend.onrender.com",
                "https://veriweave.xyz",
                "https://www.veriweave.xyz",
                "http://localhost:5173"
        };

        for (String origin : allowed) {
            mockMvc.perform(options("/api/auth/me")
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin));
        }

        // Unauthorized origin is not granted Access-Control-Allow-Origin
        mockMvc.perform(options("/api/auth/me")
                        .header("Origin", "https://malicious-site.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Frontend SPA fallback configuration file exists and contains rewrite rule")
    void shouldVerifyFrontendSpaFallbackConfiguration() throws Exception {
        File redirectsFile = new File("..\\frontend\\public\\_redirects");
        if (!redirectsFile.exists()) {
            redirectsFile = new File("frontend/public/_redirects");
        }
        if (!redirectsFile.exists()) {
            redirectsFile = new File("C:\\VeriWeave\\frontend\\public\\_redirects");
        }

        assertTrue(redirectsFile.exists(), "frontend/public/_redirects must exist for Render SPA routing");
        String content = Files.readString(redirectsFile.toPath());
        assertTrue(content.contains("/*") && content.contains("/index.html") && content.contains("200"),
                "_redirects must contain rewrite rule: /* /index.html 200");
    }
}
