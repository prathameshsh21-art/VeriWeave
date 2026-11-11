package com.veriweave.analysis.entity;

import com.veriweave.knowledge.entity.EntryCategory;
import com.veriweave.knowledge.entity.KnowledgeEntry;
import com.veriweave.knowledge.entity.Severity;
import com.veriweave.knowledge.entity.SourceVersion;
import com.veriweave.submission.entity.ContentVersion;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "findings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRun analysisRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_version_id", nullable = false)
    private ContentVersion contentVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_entry_id")
    private KnowledgeEntry knowledgeEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_version_id")
    private SourceVersion sourceVersion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private FindingType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private EntryCategory category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 35)
    private FindingClassification classification;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Column(name = "passage_start", nullable = false)
    private Integer passageStart;

    @Column(name = "passage_end", nullable = false)
    private Integer passageEnd;

    @Column(name = "passage_quote", nullable = false, columnDefinition = "TEXT")
    private String passageQuote;

    @Column(name = "evidence_quote", columnDefinition = "TEXT")
    private String evidenceQuote;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "suggested_edit", columnDefinition = "TEXT")
    private String suggestedEdit;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String confidence = "HIGH";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Finding other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
