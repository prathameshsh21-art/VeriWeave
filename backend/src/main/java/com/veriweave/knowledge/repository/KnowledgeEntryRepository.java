package com.veriweave.knowledge.repository;

import com.veriweave.knowledge.entity.EntryCategory;
import com.veriweave.knowledge.entity.KnowledgeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeEntryRepository extends JpaRepository<KnowledgeEntry, Long> {

    List<KnowledgeEntry> findAllBySourceVersionIdOrderByCodeAsc(Long sourceVersionId);

    List<KnowledgeEntry> findAllBySourceVersionIdAndCategoryOrderByCodeAsc(Long sourceVersionId, EntryCategory category);

    Optional<KnowledgeEntry> findByIdAndSourceVersionId(Long id, Long sourceVersionId);

    boolean existsBySourceVersionIdAndCode(Long sourceVersionId, String code);

    long countBySourceVersionId(Long sourceVersionId);
}
