package com.veriweave.analysis.pipeline;

import com.veriweave.knowledge.entity.KnowledgeEntry;

import java.util.List;

/**
 * Provider-agnostic AI / Model proposal interface.
 * "The model proposes. Deterministic code verifies. A human decides."
 */
public interface AiClient {

    String getProviderName();

    List<ProposedFinding> proposeFindings(
            String fullSubmissionText,
            List<Passage> passages,
            List<KnowledgeEntry> candidateEntries
    );
}
