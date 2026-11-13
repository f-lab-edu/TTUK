package com.ttuk.category.dto;

public record CategoryResponse(
        Long categoryId,
        String name,
        boolean subscribed
) {

    public static CategoryResponse of(CategorySummary summary, boolean subscribed) {
        return new CategoryResponse(summary.categoryId(), summary.name(), subscribed);
    }

}
