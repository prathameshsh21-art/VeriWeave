package com.veriweave.auth.dto;

import com.veriweave.workspace.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceDto {
    private Long id;
    private String name;
    private String slug;
    private Role role;
}
