package com.veriweave.knowledge.service;

import com.veriweave.audit.service.AuditService;
import com.veriweave.auth.dto.UserDto;
import com.veriweave.knowledge.dto.*;
import com.veriweave.knowledge.entity.*;
import com.veriweave.knowledge.repository.KnowledgeEntryRepository;
import com.veriweave.knowledge.repository.KnowledgeSourceRepository;
import com.veriweave.knowledge.repository.SourceVersionRepository;
import com.veriweave.security.UserPrincipal;
import com.veriweave.workspace.entity.Role;
import com.veriweave.workspace.entity.User;
import com.veriweave.workspace.entity.Workspace;
import com.veriweave.workspace.repository.UserRepository;
import com.veriweave.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeSourceRepository sourceRepository;
    private final SourceVersionRepository versionRepository;
    private final KnowledgeEntryRepository entryRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final CanonicalChecksumService checksumService;
    private final AuditService auditService;
    private final PdfExtractionService pdfExtractionService;

    @Transactional
    public KnowledgeSourceDto createSource(Long workspaceId, CreateKnowledgeSourceRequest request, UserPrincipal principal) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + principal.getId()));

        if (sourceRepository.existsByWorkspaceIdAndTitleIgnoreCase(workspaceId, request.getTitle().trim())) {
            throw new IllegalArgumentException("A knowledge source with this title already exists in the workspace");
        }

        KnowledgeSource source = KnowledgeSource.builder()
                .workspace(workspace)
                .title(request.getTitle().trim())
                .type(request.getType())
                .archived(false)
                .build();
        source = sourceRepository.save(source);

        // Version 1 is automatically created in DRAFT status
        SourceVersion initialDraft = SourceVersion.builder()
                .source(source)
                .versionNumber(1)
                .status(VersionStatus.DRAFT)
                .changelogNotes(request.getInitialNotes() != null ? request.getInitialNotes().trim() : "Initial draft")
                .build();
        versionRepository.save(initialDraft);

        auditService.logEvent(workspace, user, "CREATE_SOURCE", "KnowledgeSource", source.getId(), "Title: " + source.getTitle());

        return toSourceDto(source, initialDraft, 0);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSourceDto> listSources(Long workspaceId) {
        return sourceRepository.findAllActiveByWorkspaceId(workspaceId).stream()
                .map(source -> {
                    SourceVersion current = source.getCurrentVersion();
                    long count = current != null ? entryRepository.countBySourceVersionId(current.getId()) : 0;
                    return toSourceDto(source, current, count);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public KnowledgeSourceDto getSource(Long workspaceId, Long sourceId) {
        KnowledgeSource source = getSourceInWorkspace(workspaceId, sourceId);
        SourceVersion current = source.getCurrentVersion();
        long count = current != null ? entryRepository.countBySourceVersionId(current.getId()) : 0;
        return toSourceDto(source, current, count);
    }

    @Transactional
    public SourceVersionDto createDraftVersion(Long workspaceId, Long sourceId, Long cloneFromVersionId, UserPrincipal principal) {
        KnowledgeSource source = getSourceInWorkspace(workspaceId, sourceId);
        User user = userRepository.findById(principal.getId()).orElseThrow();

        // Check if there is already an active DRAFT
        Optional<SourceVersion> existingDraft = versionRepository.findFirstBySourceIdAndStatus(sourceId, VersionStatus.DRAFT);
        if (existingDraft.isPresent()) {
            throw new IllegalStateException("A DRAFT version (v" + existingDraft.get().getVersionNumber() + ") already exists. Please approve or delete it before creating a new draft.");
        }

        int nextVersionNumber = versionRepository.findMaxVersionNumberBySourceId(sourceId) + 1;
        SourceVersion newDraft = SourceVersion.builder()
                .source(source)
                .versionNumber(nextVersionNumber)
                .status(VersionStatus.DRAFT)
                .changelogNotes("Draft revision v" + nextVersionNumber)
                .build();
        newDraft = versionRepository.save(newDraft);

        // Clone entries if requested
        long clonedCount = 0;
        if (cloneFromVersionId != null) {
            SourceVersion sourceToClone = getVersionInSource(sourceId, cloneFromVersionId);
            List<KnowledgeEntry> previousEntries = entryRepository.findAllBySourceVersionIdOrderByCodeAsc(sourceToClone.getId());
            for (KnowledgeEntry prev : previousEntries) {
                KnowledgeEntry clone = KnowledgeEntry.builder()
                        .sourceVersion(newDraft)
                        .code(prev.getCode())
                        .category(prev.getCategory())
                        .defaultSeverity(prev.getDefaultSeverity())
                        .text(prev.getText())
                        .pageOrSectionReference(prev.getPageOrSectionReference())
                        .build();
                entryRepository.save(clone);
                clonedCount++;
            }
        }

        auditService.logEvent(source.getWorkspace(), user, "CREATE_DRAFT_VERSION", "SourceVersion", newDraft.getId(), "Version: " + nextVersionNumber);

        return toVersionDto(newDraft, clonedCount);
    }

    @Transactional(readOnly = true)
    public List<SourceVersionDto> listVersions(Long workspaceId, Long sourceId) {
        getSourceInWorkspace(workspaceId, sourceId);
        return versionRepository.findAllBySourceIdOrderByVersionNumberDesc(sourceId).stream()
                .map(v -> toVersionDto(v, entryRepository.countBySourceVersionId(v.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SourceVersionDto getVersion(Long workspaceId, Long sourceId, Long versionId) {
        getSourceInWorkspace(workspaceId, sourceId);
        SourceVersion version = getVersionInSource(sourceId, versionId);
        return toVersionDto(version, entryRepository.countBySourceVersionId(version.getId()));
    }

    @Transactional
    public SourceVersionDto approveVersion(Long workspaceId, Long sourceId, Long versionId, UserPrincipal principal) {
        KnowledgeSource source = getSourceInWorkspace(workspaceId, sourceId);
        SourceVersion targetDraft = getVersionInSource(sourceId, versionId);
        User adminUser = userRepository.findById(principal.getId()).orElseThrow();

        if (targetDraft.getStatus() != VersionStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT versions can be approved. Current status is " + targetDraft.getStatus());
        }

        List<KnowledgeEntry> entries = entryRepository.findAllBySourceVersionIdOrderByCodeAsc(targetDraft.getId());
        if (entries.isEmpty()) {
            throw new IllegalStateException("Cannot approve a source version with zero knowledge entries. Add or import entries first.");
        }

        Instant now = Instant.now();

        // 1. Transactionally supersede current version if one exists
        Optional<SourceVersion> currentOpt = versionRepository.findFirstBySourceIdAndStatus(sourceId, VersionStatus.CURRENT);
        if (currentOpt.isPresent()) {
            SourceVersion current = currentOpt.get();
            current.setStatus(VersionStatus.SUPERSEDED);
            current.setEffectiveTo(now);
            versionRepository.save(current);
            log.info("Superseded previous current version v{} for source {}", current.getVersionNumber(), sourceId);
        }

        // 2. Compute canonical JSON SHA-256 checksum
        String checksum = checksumService.computeChecksum(entries);

        // 3. Activate target draft
        targetDraft.setStatus(VersionStatus.CURRENT);
        targetDraft.setChecksum(checksum);
        targetDraft.setApprovedBy(adminUser);
        targetDraft.setApprovedAt(now);
        targetDraft.setEffectiveFrom(now);
        targetDraft.setEffectiveTo(null);
        targetDraft = versionRepository.save(targetDraft);

        // 4. Update authoritative current_version pointer on KnowledgeSource
        source.setCurrentVersion(targetDraft);
        sourceRepository.save(source);

        // 5. Append-only audit trail
        auditService.logEvent(source.getWorkspace(), adminUser, "APPROVE_SOURCE_VERSION", "SourceVersion", targetDraft.getId(), 
                String.format("Approved v%d (Checksum: %s, Entries: %d)", targetDraft.getVersionNumber(), checksum, entries.size()));

        return toVersionDto(targetDraft, entries.size());
    }

    @Transactional
    public KnowledgeEntryDto addEntry(Long workspaceId, Long sourceId, Long versionId, CreateEntryRequest request, UserPrincipal principal) {
        getSourceInWorkspace(workspaceId, sourceId);
        SourceVersion version = getVersionInSource(sourceId, versionId);

        if (version.getStatus() != VersionStatus.DRAFT) {
            throw new IllegalStateException("Entries can only be added to DRAFT versions. Approved versions are immutable.");
        }

        String normalizedCode = request.getCode().trim().toUpperCase();
        if (entryRepository.existsBySourceVersionIdAndCode(versionId, normalizedCode)) {
            throw new IllegalArgumentException("An entry with code '" + normalizedCode + "' already exists in this version");
        }

        KnowledgeEntry entry = KnowledgeEntry.builder()
                .sourceVersion(version)
                .code(normalizedCode)
                .category(request.getCategory())
                .defaultSeverity(request.getDefaultSeverity())
                .text(request.getText().trim())
                .pageOrSectionReference(request.getPageOrSectionReference() != null ? request.getPageOrSectionReference().trim() : null)
                .build();
        entry = entryRepository.save(entry);

        return toEntryDto(entry);
    }

    @Transactional
    public KnowledgeEntryDto updateEntry(Long workspaceId, Long sourceId, Long versionId, Long entryId, CreateEntryRequest request, UserPrincipal principal) {
        getSourceInWorkspace(workspaceId, sourceId);
        SourceVersion version = getVersionInSource(sourceId, versionId);
        KnowledgeEntry entry = getEntryInVersion(versionId, entryId);

        if (version.getStatus() != VersionStatus.DRAFT) {
            throw new IllegalStateException("Entries can only be modified in DRAFT versions. Approved versions are immutable.");
        }

        String normalizedCode = request.getCode().trim().toUpperCase();
        if (!entry.getCode().equalsIgnoreCase(normalizedCode) && entryRepository.existsBySourceVersionIdAndCode(versionId, normalizedCode)) {
            throw new IllegalArgumentException("An entry with code '" + normalizedCode + "' already exists in this version");
        }

        entry.setCode(normalizedCode);
        entry.setCategory(request.getCategory());
        entry.setDefaultSeverity(request.getDefaultSeverity());
        entry.setText(request.getText().trim());
        entry.setPageOrSectionReference(request.getPageOrSectionReference() != null ? request.getPageOrSectionReference().trim() : null);
        entry = entryRepository.save(entry);

        return toEntryDto(entry);
    }

    @Transactional
    public void deleteEntry(Long workspaceId, Long sourceId, Long versionId, Long entryId, UserPrincipal principal) {
        getSourceInWorkspace(workspaceId, sourceId);
        SourceVersion version = getVersionInSource(sourceId, versionId);
        KnowledgeEntry entry = getEntryInVersion(versionId, entryId);

        if (version.getStatus() != VersionStatus.DRAFT) {
            throw new IllegalStateException("Entries can only be deleted from DRAFT versions. Approved versions are immutable.");
        }

        entryRepository.delete(entry);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeEntryDto> listEntries(Long workspaceId, Long sourceId, Long versionId) {
        getSourceInWorkspace(workspaceId, sourceId);
        getVersionInSource(sourceId, versionId);
        return entryRepository.findAllBySourceVersionIdOrderByCodeAsc(versionId).stream()
                .map(this::toEntryDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<KnowledgeEntryDto> importPdfToDraft(Long workspaceId, Long sourceId, Long versionId, byte[] pdfBytes, String filename, UserPrincipal principal) {
        getSourceInWorkspace(workspaceId, sourceId);
        SourceVersion version = getVersionInSource(sourceId, versionId);

        if (version.getStatus() != VersionStatus.DRAFT) {
            throw new IllegalStateException("PDF entries can only be imported into DRAFT versions. Approved versions are immutable.");
        }

        version.setFileReference(filename != null ? filename : "uploaded-guidelines.pdf");
        versionRepository.save(version);

        List<CreateEntryRequest> extracted = pdfExtractionService.extractCandidateEntries(pdfBytes, "RULE");
        for (CreateEntryRequest req : extracted) {
            // Adjust code if duplicate
            String code = req.getCode();
            int suffix = 1;
            while (entryRepository.existsBySourceVersionIdAndCode(versionId, code)) {
                code = req.getCode() + "-" + suffix++;
            }

            KnowledgeEntry entry = KnowledgeEntry.builder()
                    .sourceVersion(version)
                    .code(code)
                    .category(req.getCategory())
                    .defaultSeverity(req.getDefaultSeverity())
                    .text(req.getText())
                    .pageOrSectionReference(req.getPageOrSectionReference())
                    .build();
            entryRepository.save(entry);
        }

        return listEntries(workspaceId, sourceId, versionId);
    }

    @Transactional
    public void archiveSource(Long workspaceId, Long sourceId, UserPrincipal principal) {
        KnowledgeSource source = getSourceInWorkspace(workspaceId, sourceId);
        User user = userRepository.findById(principal.getId()).orElseThrow();
        source.setArchived(true);
        sourceRepository.save(source);
        auditService.logEvent(source.getWorkspace(), user, "ARCHIVE_SOURCE", "KnowledgeSource", source.getId(), "Title: " + source.getTitle());
    }

    // --- Parentage Hierarchy Guards ---

    private KnowledgeSource getSourceInWorkspace(Long workspaceId, Long sourceId) {
        return sourceRepository.findByIdAndWorkspaceId(sourceId, workspaceId)
                .orElseThrow(() -> new AccessDeniedException("Knowledge source " + sourceId + " not found in workspace " + workspaceId));
    }

    private SourceVersion getVersionInSource(Long sourceId, Long versionId) {
        return versionRepository.findByIdAndSourceId(versionId, sourceId)
                .orElseThrow(() -> new AccessDeniedException("Source version " + versionId + " does not belong to source " + sourceId));
    }

    private KnowledgeEntry getEntryInVersion(Long versionId, Long entryId) {
        return entryRepository.findByIdAndSourceVersionId(entryId, versionId)
                .orElseThrow(() -> new AccessDeniedException("Knowledge entry " + entryId + " does not belong to version " + versionId));
    }

    // --- DTO Mappers ---

    private KnowledgeSourceDto toSourceDto(KnowledgeSource source, SourceVersion currentVersion, long entryCount) {
        return KnowledgeSourceDto.builder()
                .id(source.getId())
                .workspaceId(source.getWorkspace().getId())
                .title(source.getTitle())
                .type(source.getType())
                .archived(source.isArchived())
                .currentVersion(currentVersion != null ? toVersionDto(currentVersion, entryCount) : null)
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .build();
    }

    private SourceVersionDto toVersionDto(SourceVersion v, long entryCount) {
        return SourceVersionDto.builder()
                .id(v.getId())
                .sourceId(v.getSource().getId())
                .versionNumber(v.getVersionNumber())
                .status(v.getStatus())
                .checksum(v.getChecksum())
                .fileReference(v.getFileReference())
                .approvedBy(v.getApprovedBy() != null ? UserDto.builder()
                        .id(v.getApprovedBy().getId())
                        .email(v.getApprovedBy().getEmail())
                        .fullName(v.getApprovedBy().getFullName())
                        .build() : null)
                .approvedAt(v.getApprovedAt())
                .effectiveFrom(v.getEffectiveFrom())
                .effectiveTo(v.getEffectiveTo())
                .changelogNotes(v.getChangelogNotes())
                .entryCount(entryCount)
                .build();
    }

    private KnowledgeEntryDto toEntryDto(KnowledgeEntry e) {
        return KnowledgeEntryDto.builder()
                .id(e.getId())
                .sourceVersionId(e.getSourceVersion().getId())
                .code(e.getCode())
                .category(e.getCategory())
                .defaultSeverity(e.getDefaultSeverity())
                .text(e.getText())
                .pageOrSectionReference(e.getPageOrSectionReference())
                .build();
    }
}
