package com.veriweave.knowledge.controller;

import com.veriweave.common.ApiResponse;
import com.veriweave.knowledge.dto.*;
import com.veriweave.knowledge.service.KnowledgeService;
import com.veriweave.security.UserPrincipal;
import com.veriweave.security.WorkspaceSecurityService;
import com.veriweave.workspace.entity.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping({"/api/workspaces/{workspaceId}/sources", "/api/workspaces/{workspaceId}/knowledge/sources"})
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final WorkspaceSecurityService workspaceSecurity;

    // --- Knowledge Sources ---

    @PostMapping
    public ResponseEntity<ApiResponse<KnowledgeSourceDto>> createSource(
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateKnowledgeSourceRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN);
        KnowledgeSourceDto dto = knowledgeService.createSource(workspaceId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Source created", dto));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<KnowledgeSourceDto>>> listSources(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal);
        List<KnowledgeSourceDto> sources = knowledgeService.listSources(workspaceId);
        return ResponseEntity.ok(ApiResponse.success(sources));
    }

    @GetMapping("/{sourceId}")
    public ResponseEntity<ApiResponse<KnowledgeSourceDto>> getSource(
            @PathVariable Long workspaceId,
            @PathVariable Long sourceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal);
        KnowledgeSourceDto dto = knowledgeService.getSource(workspaceId, sourceId);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @DeleteMapping("/{sourceId}")
    public ResponseEntity<ApiResponse<Void>> archiveSource(
            @PathVariable Long workspaceId,
            @PathVariable Long sourceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN);
        knowledgeService.archiveSource(workspaceId, sourceId, principal);
        return ResponseEntity.ok(ApiResponse.success("Source archived successfully", null));
    }

    // --- Source Versions ---

    @PostMapping("/{sourceId}/versions")
    public ResponseEntity<ApiResponse<SourceVersionDto>> createDraftVersion(
            @PathVariable Long workspaceId,
            @PathVariable Long sourceId,
            @RequestParam(required = false) Long cloneFromVersionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN);
        SourceVersionDto dto = knowledgeService.createDraftVersion(workspaceId, sourceId, cloneFromVersionId, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Draft version created", dto));
    }

    @GetMapping("/{sourceId}/versions")
    public ResponseEntity<ApiResponse<List<SourceVersionDto>>> listVersions(
            @PathVariable Long workspaceId,
            @PathVariable Long sourceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal);
        List<SourceVersionDto> versions = knowledgeService.listVersions(workspaceId, sourceId);
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    @GetMapping("/{sourceId}/versions/{versionId}")
    public ResponseEntity<ApiResponse<SourceVersionDto>> getVersion(
            @PathVariable Long workspaceId,
            @PathVariable Long sourceId,
            @PathVariable Long versionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal);
        SourceVersionDto dto = knowledgeService.getVersion(workspaceId, sourceId, versionId);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping("/{sourceId}/versions/{versionId}/approve")
    public ResponseEntity<ApiResponse<SourceVersionDto>> approveVersion(
            @PathVariable Long workspaceId,
            @PathVariable Long sourceId,
            @PathVariable Long versionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN);
        SourceVersionDto dto = knowledgeService.approveVersion(workspaceId, sourceId, versionId, principal);
        return ResponseEntity.ok(ApiResponse.success("Version approved as CURRENT", dto));
    }

    // --- Knowledge Entries ---

    @GetMapping("/{sourceId}/versions/{versionId}/entries")
    public ResponseEntity<ApiResponse<List<KnowledgeEntryDto>>> listEntries(
            @PathVariable Long workspaceId,
            @PathVariable Long sourceId,
            @PathVariable Long versionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal);
        List<KnowledgeEntryDto> entries = knowledgeService.listEntries(workspaceId, sourceId, versionId);
        return ResponseEntity.ok(ApiResponse.success(entries));
    }

    @PostMapping("/{sourceId}/versions/{versionId}/entries")
    public ResponseEntity<ApiResponse<KnowledgeEntryDto>> addEntry(
            @PathVariable Long workspaceId,
            @PathVariable Long sourceId,
            @PathVariable Long versionId,
            @Valid @RequestBody CreateEntryRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN);
        KnowledgeEntryDto dto = knowledgeService.addEntry(workspaceId, sourceId, versionId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Entry added to draft", dto));
    }

    @PutMapping("/{sourceId}/versions/{versionId}/entries/{entryId}")
    public ResponseEntity<ApiResponse<KnowledgeEntryDto>> updateEntry(
            @PathVariable Long workspaceId,
            @PathVariable Long sourceId,
            @PathVariable Long versionId,
            @PathVariable Long entryId,
            @Valid @RequestBody CreateEntryRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN);
        KnowledgeEntryDto dto = knowledgeService.updateEntry(workspaceId, sourceId, versionId, entryId, request, principal);
        return ResponseEntity.ok(ApiResponse.success("Entry updated", dto));
    }

    @DeleteMapping("/{sourceId}/versions/{versionId}/entries/{entryId}")
    public ResponseEntity<ApiResponse<Void>> deleteEntry(
            @PathVariable Long workspaceId,
            @PathVariable Long sourceId,
            @PathVariable Long versionId,
            @PathVariable Long entryId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN);
        knowledgeService.deleteEntry(workspaceId, sourceId, versionId, entryId, principal);
        return ResponseEntity.ok(ApiResponse.success("Entry deleted", null));
    }

    // --- PDF Import ---

    @PostMapping(value = {
            "/{sourceId}/versions/{versionId}/import-pdf",
            "/{sourceId}/versions/{versionId}/from-pdf"
    }, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<KnowledgeEntryDto>>> importPdf(
            @PathVariable Long workspaceId,
            @PathVariable Long sourceId,
            @PathVariable Long versionId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal
    ) throws IOException {
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN);
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("File is empty", "BAD_REQUEST"));
        }
        List<KnowledgeEntryDto> entries = knowledgeService.importPdfToDraft(
                workspaceId, sourceId, versionId, file.getBytes(), file.getOriginalFilename(), principal
        );
        return ResponseEntity.ok(ApiResponse.success("Imported " + entries.size() + " reviewable entries from PDF", entries));
    }

    @PostMapping(value = {
            "/{sourceId}/versions/from-pdf",
            "/{sourceId}/versions/import-pdf"
    }, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SourceVersionDto>> importPdfNewDraft(
            @PathVariable Long workspaceId,
            @PathVariable Long sourceId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal
    ) throws IOException {
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN);
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("File is empty", "BAD_REQUEST"));
        }
        SourceVersionDto draft = knowledgeService.createDraftVersion(workspaceId, sourceId, null, principal);
        knowledgeService.importPdfToDraft(
                workspaceId, sourceId, draft.getId(), file.getBytes(), file.getOriginalFilename(), principal
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Draft version created from PDF", draft));
    }
}
