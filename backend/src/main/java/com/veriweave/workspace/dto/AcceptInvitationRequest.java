package com.veriweave.workspace.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcceptInvitationRequest {

    private String fullName;
    private String password;
}
