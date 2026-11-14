package com.veriweave.auth.controller;

import com.veriweave.auth.dto.*;
import com.veriweave.auth.service.AuthService;
import com.veriweave.common.ApiResponse;
import com.veriweave.common.email.EmailService;
import com.veriweave.security.RateLimitingService;
import com.veriweave.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimitingService rateLimitingService;
    private final EmailService emailService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest.getRemoteAddr();
        rateLimitingService.checkRateLimit("register:" + clientIp, 5, 60); // 5 per min

        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest.getRemoteAddr();
        rateLimitingService.checkRateLimit("login:" + clientIp, 10, 60); // 10 per min

        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest.getRemoteAddr();
        rateLimitingService.checkRateLimit("verify:" + clientIp, 6, 300); // 6 per 5 mins

        AuthResponse response = authService.verifyEmail(request);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", response));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest.getRemoteAddr();
        rateLimitingService.checkRateLimit("resend:" + clientIp, 3, 300); // 3 per 5 mins

        authService.resendVerification(request);
        return ResponseEntity.ok(ApiResponse.success("Verification code dispatched", null));
    }

    @PostMapping("/change-email")
    public ResponseEntity<ApiResponse<Void>> changeEmail(
            @Valid @RequestBody ChangeEmailRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest.getRemoteAddr();
        rateLimitingService.checkRateLimit("change_email:" + clientIp, 3, 300);

        authService.changeEmail(request);
        return ResponseEntity.ok(ApiResponse.success("Email updated and new verification code sent", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest.getRemoteAddr();
        rateLimitingService.checkRateLimit("forgot_pw:" + clientIp, 5, 600); // 5 per 10 mins

        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("If this email is registered, a password reset verification code has been dispatched.", null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest.getRemoteAddr();
        rateLimitingService.checkRateLimit("reset_pw:" + clientIp, 5, 600); // 5 per 10 mins

        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password has been successfully reset. Please log in with your new password.", null));
    }

    @GetMapping("/email-service-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEmailServiceStatus() {
        boolean configured = emailService.isConfigured();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "configured", configured,
                "providerStatus", configured ? "READY_FOR_PRODUCTION" : "NOT_READY_FOR_PRODUCTION",
                "message", configured
                        ? "SMTP mail provider is active and ready for live verification codes."
                        : "SMTP provider is unconfigured. For CodeTitan evaluation, use pre-seeded demo accounts (Secret123!)."
        )));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authenticated", "UNAUTHORIZED"));
        }
        AuthResponse response = authService.getCurrentUser(principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
