package com.veriweave.security;

import com.veriweave.workspace.entity.Membership;
import com.veriweave.workspace.entity.Role;
import com.veriweave.workspace.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Optional;

@Service("workspaceSecurity")
@RequiredArgsConstructor
public class WorkspaceSecurityService {

    private final MembershipRepository membershipRepository;

    @Transactional(readOnly = true)
    public boolean isMember(Long workspaceId, UserPrincipal principal) {
        if (workspaceId == null || principal == null) {
            return false;
        }
        return membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, principal.getId());
    }

    @Transactional(readOnly = true)
    public boolean hasRole(Long workspaceId, UserPrincipal principal, Role... allowedRoles) {
        if (workspaceId == null || principal == null) {
            return false;
        }
        Optional<Membership> membership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, principal.getId());
        if (membership.isEmpty()) {
            return false;
        }
        Role userRole = membership.get().getRole();
        return Arrays.asList(allowedRoles).contains(userRole);
    }

    @Transactional(readOnly = true)
    public Membership requireMembership(Long workspaceId, UserPrincipal principal, Role... allowedRoles) {
        if (workspaceId == null || principal == null) {
            throw new AccessDeniedException("Authentication and workspace context are required");
        }
        Membership membership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, principal.getId())
                .orElseThrow(() -> new AccessDeniedException("User is not a member of workspace ID: " + workspaceId));

        if (allowedRoles != null && allowedRoles.length > 0) {
            boolean hasAllowedRole = Arrays.asList(allowedRoles).contains(membership.getRole());
            if (!hasAllowedRole) {
                throw new AccessDeniedException("Required role not possessed in workspace. Current: " + membership.getRole());
            }
        }
        return membership;
    }
}
