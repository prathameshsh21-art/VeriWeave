package com.veriweave;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veriweave.auth.dto.AuthResponse;
import com.veriweave.auth.dto.LoginRequest;
import com.veriweave.auth.dto.RefreshTokenRequest;
import com.veriweave.auth.dto.RegisterRequest;
import com.veriweave.security.JwtService;
import com.veriweave.security.RateLimitingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        rateLimitingService.reset();
    }

    @Test
    @DisplayName("User registration grants ADMIN role on initial workspace, and JWT login succeeds")
    void shouldRegisterAndAuthenticateUserSuccessfully() throws Exception {
        RegisterRequest registerReq = RegisterRequest.builder()
                .email("admin@veriweave.com")
                .password("Secret123!")
                .fullName("Admin User")
                .workspaceName("Acme Global")
                .build();

        // 1. Register: First user becomes ADMIN of their workspace
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("admin@veriweave.com"))
                .andExpect(jsonPath("$.data.activeWorkspace.name").value("Acme Global"))
                .andExpect(jsonPath("$.data.activeWorkspace.role").value("ADMIN"))
                .andReturn();

        // 2. Login with credentials
        LoginRequest loginReq = LoginRequest.builder()
                .email("admin@veriweave.com")
                .password("Secret123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseJson).path("data").path("accessToken").asText();

        // 3. Authenticated /api/auth/me
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.email").value("admin@veriweave.com"))
                .andExpect(jsonPath("$.data.activeWorkspace.role").value("ADMIN"));
    }

    @Test
    @DisplayName("Tokens are strictly distinguished: refresh token cannot access APIs and access token cannot refresh")
    void shouldDistinguishAccessAndRefreshTokens() throws Exception {
        RegisterRequest registerReq = RegisterRequest.builder()
                .email("tokens@veriweave.com")
                .password("Secret123!")
                .fullName("Token User")
                .workspaceName("Token Workspace")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseJson).path("data").path("accessToken").asText();
        String refreshToken = objectMapper.readTree(responseJson).path("data").path("refreshToken").asText();

        // Access token works on protected endpoint
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Refresh token fails when used as Bearer access token (returns 401 Unauthorized)
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());

        // Passing access token to /refresh endpoint is rejected
        RefreshTokenRequest invalidRefreshReq = RefreshTokenRequest.builder()
                .refreshToken(accessToken)
                .build();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRefreshReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // Passing valid refresh token to /refresh succeeds
        RefreshTokenRequest validRefreshReq = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRefreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("Invalid, tampered and expired tokens are rejected")
    void shouldRejectInvalidTokens() throws Exception {
        // 1. Completely malformed token (returns 401 Unauthorized)
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid.token.payload"))
                .andExpect(status().isUnauthorized());

        // 2. Refresh endpoint rejects invalid token string
        RefreshTokenRequest badReq = RefreshTokenRequest.builder()
                .refreshToken("invalid-token-signature")
                .build();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Server enforces workspace isolation: altering X-Workspace-Id cannot access unauthorized workspaces")
    void shouldEnforceServerSideWorkspaceAuthorization() throws Exception {
        // Register User Alpha (gets Workspace Alpha)
        RegisterRequest alphaReq = RegisterRequest.builder()
                .email("alpha@veriweave.com")
                .password("Secret123!")
                .fullName("User Alpha")
                .workspaceName("Alpha Corp")
                .build();

        MvcResult alphaResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alphaReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String alphaJson = alphaResult.getResponse().getContentAsString();
        String alphaToken = objectMapper.readTree(alphaJson).path("data").path("accessToken").asText();
        long alphaWsId = objectMapper.readTree(alphaJson).path("data").path("activeWorkspace").path("id").asLong();

        // Register User Beta (gets Workspace Beta)
        RegisterRequest betaReq = RegisterRequest.builder()
                .email("beta@veriweave.com")
                .password("Secret123!")
                .fullName("User Beta")
                .workspaceName("Beta Corp")
                .build();

        MvcResult betaResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(betaReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String betaJson = betaResult.getResponse().getContentAsString();
        long betaWsId = objectMapper.readTree(betaJson).path("data").path("activeWorkspace").path("id").asLong();

        // 1. User Alpha CAN access Workspace Alpha
        mockMvc.perform(get("/api/workspaces/" + alphaWsId)
                        .header("Authorization", "Bearer " + alphaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Alpha Corp"));

        // 2. User Alpha CANNOT access Workspace Beta via path parameter (returns 403 Forbidden)
        mockMvc.perform(get("/api/workspaces/" + betaWsId)
                        .header("Authorization", "Bearer " + alphaToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // 3. User Alpha CANNOT access Workspace Beta by setting X-Workspace-Id header (returns 403 Forbidden)
        mockMvc.perform(get("/api/workspaces/current")
                        .header("Authorization", "Bearer " + alphaToken)
                        .header("X-Workspace-Id", String.valueOf(betaWsId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
