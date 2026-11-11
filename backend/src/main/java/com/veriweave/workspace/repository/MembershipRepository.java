package com.veriweave.workspace.repository;

import com.veriweave.workspace.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    @Query("SELECT m FROM Membership m JOIN FETCH m.workspace WHERE m.user.id = :userId")
    List<Membership> findAllByUserIdWithWorkspace(@Param("userId") Long userId);

    @Query("SELECT m FROM Membership m JOIN FETCH m.user WHERE m.workspace.id = :workspaceId")
    List<Membership> findAllByWorkspaceIdWithUser(@Param("workspaceId") Long workspaceId);

    Optional<Membership> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    boolean existsByWorkspaceIdAndUserId(Long workspaceId, Long userId);
}
