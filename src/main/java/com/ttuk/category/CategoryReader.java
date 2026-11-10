package com.ttuk.category;

import com.ttuk.category.dto.CategorySummaries;
import com.ttuk.category.dto.CategorySummary;
import com.ttuk.global.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryReader {

    private final CategoryRepository categoryRepository;

    @Cacheable(cacheNames = CacheConfig.CATEGORIES, key = "'all'", sync = true)
    public CategorySummaries findAll(){
        log.info("카테고리 목록 DB 조회 (캐시 없음)");
        List<Category> categories = categoryRepository.findAll(Sort.by("id"));

        List<CategorySummary> summaries = new ArrayList<>();
        for (Category category : categories) {
            summaries.add(CategorySummary.from(category));
        }
        return new CategorySummaries(summaries);

    }

}
