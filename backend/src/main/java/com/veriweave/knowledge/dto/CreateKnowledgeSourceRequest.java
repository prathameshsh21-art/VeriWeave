package com.veriweave.knowledge.dto;

import com.veriweave.knowledge.entity.SourceType;
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
public class CreateKnowledgeSourceRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Source type is required")
    private SourceType type;

    private String initialNotes;
}
