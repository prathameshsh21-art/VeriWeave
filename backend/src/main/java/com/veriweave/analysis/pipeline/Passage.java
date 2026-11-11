package com.veriweave.analysis.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Passage {
    private int index;
    private int startOffset;
    private int endOffset;
    private String text;
}
