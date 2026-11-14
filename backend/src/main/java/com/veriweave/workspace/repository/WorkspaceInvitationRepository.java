package com.veriweave.workspace.repository;

import com.veriweave.workspace.entity.InvitationStatus;
import com.veriweave.workspace.entity.WorkspaceInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, Long> {

    Optional<WorkspaceInvitation> findByToken(String token);

    List<WorkspaceInvitation> findAllByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);

    List<WorkspaceInvitation> findAllByWorkspaceIdAndStatus(Long workspaceId, InvitationStatus status);

    Optional<WorkspaceInvitation> findByWorkspaceIdAndEmailAndStatus(Long workspaceId, String email, InvitationStatus status);

    boolean existsByWorkspaceIdAndEmailAndStatus(Long workspaceId, String email, InvitationStatus status);
}
