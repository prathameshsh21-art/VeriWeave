package com.veriweave.audit.service;

import com.veriweave.audit.entity.AuditEvent;
import com.veriweave.audit.repository.AuditEventRepository;
import com.veriweave.workspace.entity.User;
import com.veriweave.workspace.entity.Workspace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent logEvent(Workspace workspace, User actor, String action, String entityType, Long entityId, String metadata) {
        AuditEvent event = AuditEvent.builder()
                .workspace(workspace)
                .actor(actor)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .metadata(metadata)
                .build();
        return auditEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> getWorkspaceAuditTrail(Long workspaceId) {
        return auditEventRepository.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
    }
}
