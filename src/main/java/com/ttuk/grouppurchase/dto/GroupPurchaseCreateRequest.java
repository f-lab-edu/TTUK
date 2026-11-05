package com.ttuk.grouppurchase.dto;

import com.ttuk.grouppurchase.DiscountType;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record GroupPurchaseCreateRequest(

        @NotNull
        Long categoryId,

        @NotBlank
        @Size(min = 2, max = 50)
        String title,

        @Size(max = 1000)
        String description,

        @NotBlank
        @Size(max = 10)
        String unit,

        @NotNull
        @Positive
        Integer basePrice,

        @NotNull
        @Positive
        Integer floorPrice,

        @NotNull
        DiscountType discountType,

        @NotNull
        @Positive
        Integer discountValue,

        @NotNull
        @Min(2)
        Integer discountStepSize,

        @Positive
        Integer maxHeadcount,

        @NotNull
        LocalDateTime closeAt



) {
}
