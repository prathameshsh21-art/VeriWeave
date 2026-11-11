package com.veriweave.knowledge.service;

import com.veriweave.knowledge.dto.CreateEntryRequest;
import com.veriweave.knowledge.entity.EntryCategory;
import com.veriweave.knowledge.entity.Severity;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PdfExtractionService {

    /**
     * Extracts text page-by-page from an uploaded PDF and creates unapproved draft candidate entries.
     * Note: PDFBox is used purely for text extraction with page numbers.
     * The resulting entries remain in DRAFT status and must be reviewed, edited, or approved by an ADMIN.
     */
    public List<CreateEntryRequest> extractCandidateEntries(byte[] pdfBytes, String codePrefix) {
        List<CreateEntryRequest> candidates = new ArrayList<>();
        String prefix = (codePrefix != null && !codePrefix.isBlank()) ? codePrefix.trim().toUpperCase() : "RULE";

        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            int totalPages = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();

            int candidateIndex = 1;
            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);

                if (pageText == null || pageText.isBlank()) {
                    continue;
                }

                // Split page text into non-empty paragraphs
                String[] paragraphs = pageText.split("(?m)^\\s*$");
                for (String rawParagraph : paragraphs) {
                    String paragraph = rawParagraph.replaceAll("\\s+", " ").trim();
                    if (paragraph.length() < 15) {
                        continue; // Skip isolated header lines or page numbers
                    }

                    // Determine sensible default category based on content keywords
                    EntryCategory category = inferCategory(paragraph);
                    Severity severity = (category == EntryCategory.CLAIM || category == EntryCategory.FACT) 
                            ? Severity.CRITICAL 
                            : Severity.MAJOR;

                    candidates.add(CreateEntryRequest.builder()
                            .code(String.format("%s-P%02d-%02d", prefix, page, candidateIndex++))
                            .category(category)
                            .defaultSeverity(severity)
                            .text(paragraph)
                            .pageOrSectionReference("Page " + page)
                            .build());
                }
            }
        } catch (IOException e) {
            log.error("Failed to parse PDF document", e);
            throw new IllegalArgumentException("Invalid or unreadable PDF document: " + e.getMessage());
        }

        return candidates;
    }

    private EntryCategory inferCategory(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("claim") || lower.contains("proven") || lower.contains("certified") || lower.contains("clinically")) {
            return EntryCategory.CLAIM;
        } else if (lower.contains("term") || lower.contains("naming") || lower.contains("never say") || lower.contains("prohibited")) {
            return EntryCategory.TERMINOLOGY;
        } else if (lower.contains("fact") || lower.contains("founded") || lower.contains("revenue") || lower.contains("metric")) {
            return EntryCategory.FACT;
        } else if (lower.contains("contrast") || lower.contains("accessibility") || lower.contains("alt text") || lower.contains("wcag")) {
            return EntryCategory.ACCESSIBILITY;
        } else if (lower.contains("logo") || lower.contains("color") || lower.contains("font") || lower.contains("image")) {
            return EntryCategory.VISUAL;
        } else if (lower.contains("message") || lower.contains("mission") || lower.contains("pillar")) {
            return EntryCategory.MESSAGING;
        }
        return EntryCategory.TONE;
    }
}
