package com.veriweave;

import com.veriweave.analysis.entity.FindingType;
import com.veriweave.analysis.pipeline.AiClient;
import com.veriweave.analysis.pipeline.Passage;
import com.veriweave.analysis.pipeline.ProposedFinding;
import com.veriweave.analysis.pipeline.RuleBasedAiClient;
import com.veriweave.knowledge.entity.EntryCategory;
import com.veriweave.knowledge.entity.KnowledgeEntry;
import com.veriweave.knowledge.entity.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedAiClientTest {

    private final RuleBasedAiClient client = new RuleBasedAiClient();

    @Test
    @DisplayName("TERM-01: Prohibits 'miracle cure' and 'instant detox', suggests 'sustained botanical balance'")
    void testTerm01ReplacementAndProhibition() {
        KnowledgeEntry term01 = KnowledgeEntry.builder()
                .id(101L)
                .code("TERM-01")
                .category(EntryCategory.TERMINOLOGY)
                .defaultSeverity(Severity.CRITICAL)
                .text("Never say 'miracle cure' or 'instant detox'. All wellness statements must cite sustained botanical balance.")
                .build();

        String rawCopy = "Our elixir is an instant detox and a miracle cure for fine lines.";
        List<Passage> passages = List.of(
                Passage.builder().index(0).startOffset(0).endOffset(rawCopy.length()).text(rawCopy).build()
        );

        List<ProposedFinding> findings = client.proposeFindings(rawCopy, passages, List.of(term01));

        assertThat(findings).hasSize(2);

        for (ProposedFinding f : findings) {
            assertThat(f.getSuggestedEdit())
                    .as("Suggested replacement must NOT contain the prohibited phrases")
                    .doesNotContain("miracle cure")
                    .doesNotContain("instant detox")
                    .contains("sustained botanical balance");
        }
    }

    @Test
    @DisplayName("TONE-01: Prohibits 'cheap', does not trigger on 'accessible', suggests 'accessible'")
    void testTone01ReplacementAndProhibition() {
        KnowledgeEntry tone01 = KnowledgeEntry.builder()
                .id(102L)
                .code("TONE-01")
                .category(EntryCategory.TONE)
                .defaultSeverity(Severity.MAJOR)
                .text("Do not use 'cheap' when describing our skincare formulations; use 'accessible' or 'exceptional value'.")
                .build();

        String nonCompliantCopy = "Our serum is a cheap everyday formulation.";
        List<Passage> passages1 = List.of(
                Passage.builder().index(0).startOffset(0).endOffset(nonCompliantCopy.length()).text(nonCompliantCopy).build()
        );

        List<ProposedFinding> findings = client.proposeFindings(nonCompliantCopy, passages1, List.of(tone01));

        assertThat(findings).hasSize(1);
        ProposedFinding finding = findings.get(0);
        assertThat(finding.getSuggestedEdit())
                .as("Suggested replacement must NOT contain 'cheap'")
                .doesNotContain("cheap")
                .contains("accessible");

        // Now test approved wording copy: it should NOT trigger any finding!
        String compliantCopy = "Our serum is an accessible everyday formulation.";
        List<Passage> passages2 = List.of(
                Passage.builder().index(0).startOffset(0).endOffset(compliantCopy.length()).text(compliantCopy).build()
        );
        List<ProposedFinding> compliantFindings = client.proposeFindings(compliantCopy, passages2, List.of(tone01));

        assertThat(compliantFindings)
                .as("Compliant copy using approved terms must NOT trigger violations")
                .isEmpty();
    }
}
