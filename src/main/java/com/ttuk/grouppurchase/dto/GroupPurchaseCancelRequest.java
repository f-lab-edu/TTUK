package com.ttuk.grouppurchase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupPurchaseCancelRequest(

        @NotBlank
        @Size(max = 200)
        String reason

) {
}
