package com.veriweave.analysis.pipeline;

import com.veriweave.analysis.entity.FindingType;
import com.veriweave.knowledge.entity.EntryCategory;
import com.veriweave.knowledge.entity.KnowledgeEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class RuleBasedAiClient implements AiClient {

    @Override
    public String getProviderName() {
        return "VeriWeave-Deterministic-Engine-v1";
    }

    @Override
    public List<ProposedFinding> proposeFindings(
            String fullSubmissionText,
            List<Passage> passages,
            List<KnowledgeEntry> candidateEntries
    ) {
        List<ProposedFinding> proposals = new ArrayList<>();

        if (fullSubmissionText == null || passages == null || candidateEntries == null) {
            return proposals;
        }

        for (KnowledgeEntry entry : candidateEntries) {
            String ruleText = entry.getText();
            List<String> triggerPhrases = extractTriggerPhrases(ruleText);

            for (String phrase : triggerPhrases) {
                if (phrase.length() < 3) continue;

                Pattern pattern = Pattern.compile("\\b" + Pattern.quote(phrase) + "\\b", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(fullSubmissionText);

                while (matcher.find()) {
                    int start = matcher.start();
                    int end = matcher.end();
                    String matchedText = fullSubmissionText.substring(start, end);

                    // Find which passage bounds this match
                    int pStart = start;
                    int pEnd = end;
                    for (Passage p : passages) {
                        if (start >= p.getStartOffset() && end <= p.getEndOffset()) {
                            // Expand to sentence or whole passage snippet for context
                            pStart = Math.max(p.getStartOffset(), start - 30);
                            while (pStart > p.getStartOffset() && !Character.isWhitespace(fullSubmissionText.charAt(pStart))) {
                                pStart--;
                            }
                            pEnd = Math.min(p.getEndOffset(), end + 30);
                            while (pEnd < p.getEndOffset() && !Character.isWhitespace(fullSubmissionText.charAt(pEnd - 1))) {
                                pEnd++;
                            }
                            break;
                        }
                    }

                    String passageSnippet = fullSubmissionText.substring(pStart, pEnd).trim();
                    int snippetStart = fullSubmissionText.indexOf(passageSnippet, pStart);
                    int snippetEnd = snippetStart + passageSnippet.length();

                    proposals.add(ProposedFinding.builder()
                            .passageStart(snippetStart)
                            .passageEnd(snippetEnd)
                            .passageQuote(passageSnippet)
                            .knowledgeEntryId(entry.getId())
                            .evidenceQuote(extractEvidenceSnippet(ruleText, phrase))
                            .category(entry.getCategory())
                            .type(entry.getCategory() == EntryCategory.CLAIM ? FindingType.VIOLATION : FindingType.WARNING)
                            .explanation(generateExplanation(entry, matchedText))
                            .suggestedEdit(generateSuggestedEdit(entry, passageSnippet, matchedText))
                            .confidence("HIGH")
                            .isAdvisoryOnly(false)
                            .build());
                }
            }
        }

        return proposals;
    }

    private List<String> extractTriggerPhrases(String ruleText) {
        List<String> triggers = new ArrayList<>();
        // Extract phrases in quotes e.g. "cheap", "clinically proven", etc.
        Matcher quoteMatcher = Pattern.compile("[\"']([^\"']+)[\"']").matcher(ruleText);
        while (quoteMatcher.find()) {
            triggers.add(quoteMatcher.group(1));
        }

        // Common guideline trigger patterns
        if (triggers.isEmpty()) {
            Matcher forbidMatcher = Pattern.compile("(?:never say|do not use|prohibited|forbidden|avoid)\\s+([a-zA-Z0-9\\s]{3,30})", Pattern.CASE_INSENSITIVE).matcher(ruleText);
            if (forbidMatcher.find()) {
                triggers.add(forbidMatcher.group(1).trim());
            }
        }

        return triggers;
    }

    private String extractEvidenceSnippet(String fullRuleText, String phrase) {
        int idx = fullRuleText.toLowerCase().indexOf(phrase.toLowerCase());
        if (idx >= 0) {
            int start = Math.max(0, idx - 20);
            int end = Math.min(fullRuleText.length(), idx + phrase.length() + 30);
            return fullRuleText.substring(start, end).trim();
        }
        return fullRuleText.length() > 100 ? fullRuleText.substring(0, 100) + "..." : fullRuleText;
    }

    private String generateExplanation(KnowledgeEntry entry, String matchedText) {
        return String.format("Found prohibited or unapproved phrase '%s'. Per rule [%s] (%s): %s",
                matchedText, entry.getCode(), entry.getCategory(), entry.getText());
    }

    private String generateSuggestedEdit(KnowledgeEntry entry, String passageQuote, String matchedText) {
        if (entry != null && entry.getText() != null) {
            String ruleText = entry.getText();
            Matcher m = Pattern.compile("(?:use|instead use|replace with|prefer|say)\\s+[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(ruleText);
            if (m.find()) {
                String replacement = m.group(1).trim();
                return passageQuote.replace(matchedText, replacement);
            }
        }
        String code = entry != null && entry.getCode() != null ? entry.getCode() : "RULE";
        return passageQuote.replace(matchedText, "[" + code + " approved alternative]");
    }
}
