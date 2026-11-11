package com.veriweave.knowledge.repository;

import com.veriweave.knowledge.entity.KnowledgeSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, Long> {

    @Query("SELECT s FROM KnowledgeSource s LEFT JOIN FETCH s.currentVersion WHERE s.workspace.id = :workspaceId AND s.archived = false ORDER BY s.title ASC")
    List<KnowledgeSource> findAllActiveByWorkspaceId(@Param("workspaceId") Long workspaceId);

    @Query("SELECT s FROM KnowledgeSource s LEFT JOIN FETCH s.currentVersion WHERE s.id = :id AND s.workspace.id = :workspaceId")
    Optional<KnowledgeSource> findByIdAndWorkspaceId(@Param("id") Long id, @Param("workspaceId") Long workspaceId);

    boolean existsByWorkspaceIdAndTitleIgnoreCase(Long workspaceId, String title);
}
