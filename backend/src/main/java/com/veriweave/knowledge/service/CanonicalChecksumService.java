package com.veriweave.knowledge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.veriweave.knowledge.entity.KnowledgeEntry;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class CanonicalChecksumService {

    private final ObjectMapper objectMapper;

    public CanonicalChecksumService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public String computeChecksum(List<KnowledgeEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return computeSha256("[]");
        }

        // Sort deterministically by code ascending
        List<KnowledgeEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(KnowledgeEntry::getCode, String.CASE_INSENSITIVE_ORDER));

        // Create canonical representation
        List<Map<String, String>> canonicalList = new ArrayList<>();
        for (KnowledgeEntry entry : sorted) {
            Map<String, String> map = new TreeMap<>();
            map.put("category", entry.getCategory() != null ? entry.getCategory().name() : "");
            map.put("code", entry.getCode() != null ? entry.getCode().trim() : "");
            map.put("defaultSeverity", entry.getDefaultSeverity() != null ? entry.getDefaultSeverity().name() : "");
            map.put("pageOrSectionReference", entry.getPageOrSectionReference() != null ? entry.getPageOrSectionReference().trim() : "");
            map.put("text", entry.getText() != null ? entry.getText().trim() : "");
            canonicalList.add(map);
        }

        try {
            String json = objectMapper.writeValueAsString(canonicalList);
            return computeSha256(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize canonical entries for checksum", e);
        }
    }

    public String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
