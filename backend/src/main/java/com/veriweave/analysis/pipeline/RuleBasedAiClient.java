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
        if (ruleText == null || ruleText.isBlank()) {
            return triggers;
        }

        // 1. First, search for explicit prohibition patterns (e.g., Never say '...', Do not use '...', Avoid '...')
        // Captures either quoted phrases or plain words following prohibition markers
        Pattern forbidPattern = Pattern.compile(
                "(?:never\\s+say|do\\s+not\\s+use|prohibited|forbidden|avoid)\\s+(?:['\"]([^'\"]+)['\"]|([a-zA-Z0-9\\s]{3,30}?)(?=[.;,]|$|\\band\\b|\\bor\\b|\\ball\\b))",
                Pattern.CASE_INSENSITIVE
        );
        Matcher forbidMatcher = forbidPattern.matcher(ruleText);
        while (forbidMatcher.find()) {
            String phrase = forbidMatcher.group(1);
            if (phrase == null || phrase.isBlank()) {
                phrase = forbidMatcher.group(2);
            }
            if (phrase != null && !phrase.isBlank()) {
                triggers.add(phrase.trim());
            }
        }

        // Also check if there is an "or 'instant detox'" chained prohibition following a prohibited clause
        Pattern chainedOrPattern = Pattern.compile(
                "(?:never\\s+say|do\\s+not\\s+use|prohibited|forbidden|avoid)\\s+['\"][^'\"]+['\"]\\s+(?:or|and)\\s+['\"]([^'\"]+)['\"]",
                Pattern.CASE_INSENSITIVE
        );
        Matcher chainedMatcher = chainedOrPattern.matcher(ruleText);
        while (chainedMatcher.find()) {
            String phrase = chainedMatcher.group(1);
            if (phrase != null && !phrase.isBlank() && !triggers.contains(phrase.trim())) {
                triggers.add(phrase.trim());
            }
        }

        // 2. If no explicit prohibition clauses were matched, fallback to quoted phrases
        // BUT exclude quoted phrases that immediately follow positive recommendations (use, prefer, instead use, cite)
        if (triggers.isEmpty()) {
            Matcher quoteMatcher = Pattern.compile("['\"]([^'\"]+)['\"]").matcher(ruleText);
            while (quoteMatcher.find()) {
                int matchStart = quoteMatcher.start();
                String prefix = ruleText.substring(0, matchStart).trim().toLowerCase();
                // Check if this quote is preceded by a positive recommendation keyword
                if (prefix.matches(".*\\b(use|instead use|replace with|prefer|cite|recommend)\\b\\s*$")) {
                    continue; // Skip approved alternative quotes
                }
                triggers.add(quoteMatcher.group(1));
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
        String result = passageQuote;
        if (entry != null && entry.getText() != null) {
            String ruleText = entry.getText();
            List<String> prohibited = extractTriggerPhrases(ruleText);

            String candidate = null;

            // Pattern 1: Positive recommendation with quoted target
            // e.g. "...; use 'accessible' or 'exceptional value'" or "... instead use '...'"
            // Matches 'use', 'instead use', 'replace with', 'prefer', 'cite' when preceded by start, punctuation, or conjunction
            Matcher m = Pattern.compile(
                    "(?:^|[;.,]|\\band\\b)\\s*(?:instead\\s+use|replace\\s+with|prefer|use|must\\s+cite|cite)\\s+['\"]([^'\"]+)['\"]",
                    Pattern.CASE_INSENSITIVE
            ).matcher(ruleText);
            if (m.find()) {
                String found = m.group(1).trim();
                boolean containsProhibited = prohibited.stream().anyMatch(p -> found.equalsIgnoreCase(p) || found.toLowerCase().contains(p.toLowerCase()));
                if (!containsProhibited && !found.equalsIgnoreCase(matchedText)) {
                    candidate = found;
                }
            }

            // Pattern 2: Positive unquoted prescription
            // e.g. "All wellness statements must cite sustained botanical balance."
            if (candidate == null) {
                Matcher mUnquoted = Pattern.compile(
                        "(?:^|[;.,]|\\band\\b)\\s*(?:[a-zA-Z0-9\\s]{0,30}?\\b)?(?:must\\s+cite|cite|instead\\s+use|prefer|use)\\s+([a-zA-Z0-9\\s]{3,40}?)(?=[.;,]|$)",
                        Pattern.CASE_INSENSITIVE
                ).matcher(ruleText);
                if (mUnquoted.find()) {
                    String found = mUnquoted.group(1).trim().replaceAll("^['\"]+|['\"]+$", "");
                    boolean containsProhibited = prohibited.stream().anyMatch(p -> found.equalsIgnoreCase(p) || found.toLowerCase().contains(p.toLowerCase()));
                    if (!containsProhibited && !found.equalsIgnoreCase(matchedText) && !found.isBlank()) {
                        candidate = found;
                    }
                }
            }

            if (candidate != null) {
                // Replace the matched violation in the passage
                result = result.replace(matchedText, candidate);

                // Also clean up any other prohibited phrases from the same rule that happen to be in the passage snippet
                for (String p : prohibited) {
                    if (result.toLowerCase().contains(p.toLowerCase())) {
                        result = Pattern.compile("\\b" + Pattern.quote(p) + "\\b", Pattern.CASE_INSENSITIVE)
                                .matcher(result)
                                .replaceAll(Matcher.quoteReplacement(candidate));
                    }
                }
                return result;
            }
        }
        String code = entry != null && entry.getCode() != null ? entry.getCode() : "RULE";
        return result.replace(matchedText, "[" + code + " approved alternative]");
    }
}
