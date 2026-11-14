package com.veriweave.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeEmailRequest {

    @NotBlank(message = "Current email is required")
    @Email(message = "Valid current email is required")
    private String currentEmail;

    @NotBlank(message = "New email is required")
    @Email(message = "Valid new email is required")
    private String newEmail;

    @NotBlank(message = "Password is required for email modification")
    private String password;
}
