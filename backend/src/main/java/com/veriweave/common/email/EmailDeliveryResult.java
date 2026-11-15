package com.veriweave.common.email;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EmailDeliveryResult {
    private final boolean delivered;
    private final String message;
}
