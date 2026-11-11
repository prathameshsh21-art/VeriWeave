package com.veriweave.analysis.pipeline;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TextSegmenter {

    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("(?m)(^.+?)(?=(\\r?\\n\\s*\\r?\\n)|\\z)", Pattern.DOTALL);

    public List<Passage> segment(String rawText) {
        List<Passage> passages = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            return passages;
        }

        Matcher matcher = PARAGRAPH_PATTERN.matcher(rawText);
        int index = 1;
        while (matcher.find()) {
            String text = matcher.group().trim();
            if (!text.isEmpty()) {
                int start = matcher.start();
                int end = matcher.end();
                // Adjust for trailing whitespace in match
                while (end > start && Character.isWhitespace(rawText.charAt(end - 1))) {
                    end--;
                }
                while (start < end && Character.isWhitespace(rawText.charAt(start))) {
                    start++;
                }

                passages.add(Passage.builder()
                        .index(index++)
                        .startOffset(start)
                        .endOffset(end)
                        .text(rawText.substring(start, end))
                        .build());
            }
        }

        if (passages.isEmpty() && !rawText.trim().isEmpty()) {
            passages.add(Passage.builder()
                    .index(1)
                    .startOffset(0)
                    .endOffset(rawText.length())
                    .text(rawText)
                    .build());
        }

        return passages;
    }
}
