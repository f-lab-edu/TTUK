package com.ttuk.participation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ParticipationCreateRequest(
        @NotNull
        @Min(1)
        @Max(10)
        Integer quantity
) {
}
