package com.veriweave.analysis.repository;

import com.veriweave.analysis.entity.Finding;
import com.veriweave.analysis.entity.FindingClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FindingRepository extends JpaRepository<Finding, Long> {

    @Query("SELECT f FROM Finding f LEFT JOIN FETCH f.knowledgeEntry LEFT JOIN FETCH f.sourceVersion WHERE f.contentVersion.id = :contentVersionId ORDER BY f.passageStart ASC")
    List<Finding> findAllByContentVersionIdWithDetails(@Param("contentVersionId") Long contentVersionId);

    @Query("SELECT f FROM Finding f LEFT JOIN FETCH f.knowledgeEntry LEFT JOIN FETCH f.sourceVersion WHERE f.contentVersion.id = :contentVersionId AND f.classification != :exclude ORDER BY f.passageStart ASC")
    List<Finding> findAllByContentVersionIdExcluding(@Param("contentVersionId") Long contentVersionId, @Param("exclude") FindingClassification exclude);

    List<Finding> findAllByAnalysisRunId(Long analysisRunId);
}
