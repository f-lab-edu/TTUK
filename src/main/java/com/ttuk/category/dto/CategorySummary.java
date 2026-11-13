package com.ttuk.category.dto;

import com.ttuk.category.Category;

public record CategorySummary(

        Long categoryId,
        String name

) {

    public static CategorySummary from(Category category){
        return new CategorySummary(category.getId(), category.getName());
    }

}
