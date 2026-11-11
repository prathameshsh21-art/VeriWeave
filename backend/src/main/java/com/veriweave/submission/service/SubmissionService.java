package com.veriweave.submission.service;

import com.veriweave.audit.service.AuditService;
import com.veriweave.knowledge.service.CanonicalChecksumService;
import com.veriweave.security.UserPrincipal;
import com.veriweave.submission.dto.CreateSubmissionRequest;
import com.veriweave.submission.dto.SubmissionDto;
import com.veriweave.submission.dto.ContentVersionDto;
import com.veriweave.submission.entity.ContentVersion;
import com.veriweave.submission.entity.Submission;
import com.veriweave.submission.entity.SubmissionStatus;
import com.veriweave.submission.repository.ContentVersionRepository;
import com.veriweave.submission.repository.SubmissionRepository;
import com.veriweave.workspace.entity.User;
import com.veriweave.workspace.entity.Workspace;
import com.veriweave.workspace.repository.UserRepository;
import com.veriweave.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final CanonicalChecksumService checksumService;
    private final AuditService auditService;

    @Transactional
    public SubmissionDto createSubmission(Long workspaceId, CreateSubmissionRequest request, UserPrincipal principal) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
        User user = userRepository.findById(principal.getId()).orElseThrow();

        Submission submission = Submission.builder()
                .workspace(workspace)
                .createdBy(user)
                .title(request.getTitle().trim())
                .status(SubmissionStatus.DRAFT)
                .build();
        submission = submissionRepository.save(submission);

        String rawText = request.getRawText();
        String checksum = checksumService.computeSha256(rawText);

        ContentVersion initialVersion = ContentVersion.builder()
                .submission(submission)
                .versionNumber(1)
                .rawText(rawText)
                .checksum(checksum)
                .createdBy(user)
                .build();
        initialVersion = contentVersionRepository.save(initialVersion);

        submission.setCurrentVersion(initialVersion);
        submission = submissionRepository.save(submission);

        auditService.logEvent(workspace, user, "CREATE_SUBMISSION", "Submission", submission.getId(), 
                "Title: " + submission.getTitle() + ", v1 Checksum: " + checksum);

        return toSubmissionDto(submission, initialVersion);
    }

    @Transactional
    public SubmissionDto createRevision(Long workspaceId, Long submissionId, String newRawText, UserPrincipal principal) {
        Submission submission = submissionRepository.findByIdAndWorkspaceId(submissionId, workspaceId)
                .orElseThrow(() -> new AccessDeniedException("Submission not found in workspace: " + submissionId));
        User user = userRepository.findById(principal.getId()).orElseThrow();

        int nextVerNum = contentVersionRepository.findMaxVersionNumberBySubmissionId(submissionId) + 1;
        String checksum = checksumService.computeSha256(newRawText);

        ContentVersion newVersion = ContentVersion.builder()
                .submission(submission)
                .versionNumber(nextVerNum)
                .rawText(newRawText)
                .checksum(checksum)
                .createdBy(user)
                .build();
        newVersion = contentVersionRepository.save(newVersion);

        submission.setCurrentVersion(newVersion);
        submission.setStatus(SubmissionStatus.DRAFT); // Resets to draft for re-analysis
        submission = submissionRepository.save(submission);

        auditService.logEvent(submission.getWorkspace(), user, "CREATE_REVISION", "Submission", submission.getId(),
                "New version: v" + nextVerNum);

        return toSubmissionDto(submission, newVersion);
    }

    @Transactional(readOnly = true)
    public List<SubmissionDto> listSubmissions(Long workspaceId, Long userId, boolean isCreatorOnly) {
        List<Submission> list = isCreatorOnly
                ? submissionRepository.findAllByWorkspaceIdAndCreatedById(workspaceId, userId)
                : submissionRepository.findAllByWorkspaceId(workspaceId);

        return list.stream()
                .map(s -> toSubmissionDto(s, s.getCurrentVersion()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SubmissionDto getSubmission(Long workspaceId, Long submissionId) {
        Submission submission = submissionRepository.findByIdAndWorkspaceId(submissionId, workspaceId)
                .orElseThrow(() -> new AccessDeniedException("Submission not found in workspace: " + submissionId));
        return toSubmissionDto(submission, submission.getCurrentVersion());
    }

    @Transactional(readOnly = true)
    public List<ContentVersionDto> listVersions(Long workspaceId, Long submissionId) {
        submissionRepository.findByIdAndWorkspaceId(submissionId, workspaceId)
                .orElseThrow(() -> new AccessDeniedException("Submission not found in workspace: " + submissionId));
        return contentVersionRepository.findAllBySubmissionIdOrderByVersionNumberDesc(submissionId).stream()
                .map(this::toVersionDto)
                .collect(Collectors.toList());
    }

    private SubmissionDto toSubmissionDto(Submission s, ContentVersion cv) {
        return SubmissionDto.builder()
                .id(s.getId())
                .workspaceId(s.getWorkspace().getId())
                .title(s.getTitle())
                .status(s.getStatus())
                .createdBy(s.getCreatedBy().getFullName())
                .createdByEmail(s.getCreatedBy().getEmail())
                .currentVersion(cv != null ? toVersionDto(cv) : null)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private ContentVersionDto toVersionDto(ContentVersion cv) {
        return ContentVersionDto.builder()
                .id(cv.getId())
                .submissionId(cv.getSubmission().getId())
                .versionNumber(cv.getVersionNumber())
                .rawText(cv.getRawText())
                .checksum(cv.getChecksum())
                .createdBy(cv.getCreatedBy().getFullName())
                .createdAt(cv.getCreatedAt())
                .build();
    }
}
