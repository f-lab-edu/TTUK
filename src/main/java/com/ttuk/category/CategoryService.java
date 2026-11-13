package com.ttuk.category;


import com.ttuk.category.dto.CategoryResponse;
import com.ttuk.category.dto.CategorySummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryReader categoryReader;

    public List<CategoryResponse> getCategories() {

        List<CategorySummary> summaries = categoryReader.findAll().categories();

        List<CategoryResponse> responses = new ArrayList<>();
        for (CategorySummary summary : summaries) {
            responses.add(CategoryResponse.from(summary));
        }
        return responses;
    }


}
