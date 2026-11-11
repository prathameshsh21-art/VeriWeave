package com.veriweave.common;

import com.veriweave.knowledge.dto.CreateEntryRequest;
import com.veriweave.knowledge.dto.CreateKnowledgeSourceRequest;
import com.veriweave.knowledge.dto.KnowledgeSourceDto;
import com.veriweave.knowledge.dto.SourceVersionDto;
import com.veriweave.knowledge.entity.EntryCategory;
import com.veriweave.knowledge.entity.Severity;
import com.veriweave.knowledge.entity.SourceType;
import com.veriweave.knowledge.service.KnowledgeService;
import com.veriweave.security.UserPrincipal;
import com.veriweave.workspace.entity.Membership;
import com.veriweave.workspace.entity.Role;
import com.veriweave.workspace.entity.User;
import com.veriweave.workspace.entity.Workspace;
import com.veriweave.workspace.repository.MembershipRepository;
import com.veriweave.workspace.repository.UserRepository;
import com.veriweave.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final KnowledgeService knowledgeService;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByEmail("admin@veriflora.com")) {
            log.info("Demo data already seeded.");
            return;
        }

        log.info("Seeding fictional demo data for VeriFlora Botanicals...");

        // 1. Create Workspace
        Workspace workspace = Workspace.builder()
                .name("VeriFlora Botanicals")
                .slug("veriflora")
                .build();
        workspace = workspaceRepository.save(workspace);

        // 2. Create Users
        User admin = User.builder()
                .email("admin@veriflora.com")
                .fullName("Elena Vance (Brand Director)")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .build();
        admin = userRepository.save(admin);

        User creator = User.builder()
                .email("creator@veriflora.com")
                .fullName("Marcus Chen (Content Creator)")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .build();
        creator = userRepository.save(creator);

        User reviewer = User.builder()
                .email("reviewer@veriflora.com")
                .fullName("Sarah Jenkins (Compliance Reviewer)")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .build();
        reviewer = userRepository.save(reviewer);

        // 3. Create Memberships
        membershipRepository.save(Membership.builder().workspace(workspace).user(admin).role(Role.ADMIN).build());
        membershipRepository.save(Membership.builder().workspace(workspace).user(creator).role(Role.CREATOR).build());
        membershipRepository.save(Membership.builder().workspace(workspace).user(reviewer).role(Role.REVIEWER).build());

        // 4. Create & Approve Knowledge Source 1: Tone & Style Guide
        UserPrincipal adminPrincipal = UserPrincipal.create(admin);
        KnowledgeSourceDto toneSource = knowledgeService.createSource(workspace.getId(),
                CreateKnowledgeSourceRequest.builder()
                        .title("Brand Tone of Voice 2026")
                        .type(SourceType.GUIDELINE)
                        .initialNotes("Official tone standards and terminology constraints")
                        .build(),
                adminPrincipal);

        Long toneVerId = toneSource.getCurrentVersion() != null ? toneSource.getCurrentVersion().getId() : 1L;

        knowledgeService.addEntry(workspace.getId(), toneSource.getId(), toneVerId,
                CreateEntryRequest.builder()
                        .code("TONE-01")
                        .category(EntryCategory.TONE)
                        .defaultSeverity(Severity.MAJOR)
                        .text("Do not use 'cheap' when describing our skincare formulations; use 'accessible' or 'exceptional value'.")
                        .pageOrSectionReference("Section 2.1, Tone Guide")
                        .build(),
                adminPrincipal);

        knowledgeService.addEntry(workspace.getId(), toneSource.getId(), toneVerId,
                CreateEntryRequest.builder()
                        .code("TERM-01")
                        .category(EntryCategory.TERMINOLOGY)
                        .defaultSeverity(Severity.CRITICAL)
                        .text("Never say 'miracle cure' or 'instant detox'. All wellness statements must cite sustained botanical balance.")
                        .pageOrSectionReference("Section 3.4, Prohibited Words")
                        .build(),
                adminPrincipal);

        // Approve Version 1
        knowledgeService.approveVersion(workspace.getId(), toneSource.getId(), toneVerId, adminPrincipal);

        // 5. Create & Approve Knowledge Source 2: Approved Claims Register
        KnowledgeSourceDto claimsSource = knowledgeService.createSource(workspace.getId(),
                CreateKnowledgeSourceRequest.builder()
                        .title("Approved Claims Register Q1")
                        .type(SourceType.CLAIMS_REGISTER)
                        .initialNotes("Clinical trial approvals and regulatory filings")
                        .build(),
                adminPrincipal);

        Long claimsVerId = claimsSource.getCurrentVersion() != null ? claimsSource.getCurrentVersion().getId() : 2L;

        knowledgeService.addEntry(workspace.getId(), claimsSource.getId(), claimsVerId,
                CreateEntryRequest.builder()
                        .code("CLAIM-C01")
                        .category(EntryCategory.CLAIM)
                        .defaultSeverity(Severity.CRITICAL)
                        .text("Clinically proven to improve skin hydration by 28% over 14 days (Trial C-81).")
                        .pageOrSectionReference("Page 12, Claims Register")
                        .build(),
                adminPrincipal);

        knowledgeService.approveVersion(workspace.getId(), claimsSource.getId(), claimsVerId, adminPrincipal);

        log.info("Demo data seeding completed successfully for workspace VeriFlora Botanicals!");
    }
}
