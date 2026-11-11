package com.veriweave.audit.repository;

import com.veriweave.audit.entity.AuditEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    @EntityGraph(attributePaths = {"actor", "workspace"})
    List<AuditEvent> findAllByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);
    List<AuditEvent> findAllByWorkspaceIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(Long workspaceId, String entityType, Long entityId);
}
