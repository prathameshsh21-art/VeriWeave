package com.veriweave.knowledge.dto;

import com.veriweave.knowledge.entity.EntryCategory;
import com.veriweave.knowledge.entity.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEntryRequest {

    @NotBlank(message = "Entry code is required (e.g. TONE-01)")
    private String code;

    @NotNull(message = "Category is required")
    private EntryCategory category;

    @NotNull(message = "Default severity is required")
    private Severity defaultSeverity;

    @NotBlank(message = "Rule or fact text is required")
    private String text;

    private String pageOrSectionReference;
}
