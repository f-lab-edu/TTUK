package com.ttuk.category.dto;

public record CategoryResponse(
        Long categoryId,
        String name
) {

    public static CategoryResponse from(CategorySummary summary) {
        return new CategoryResponse(summary.categoryId(), summary.name());
    }

}
