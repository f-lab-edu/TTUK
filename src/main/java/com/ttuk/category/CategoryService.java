package com.ttuk.category;


import com.ttuk.category.dto.CategoryResponse;
import com.ttuk.category.dto.CategorySummary;
import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import com.ttuk.member.Member;
import com.ttuk.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryReader categoryReader;
    private final CategoryRepository categoryRepository;
    private final MemberCategoryRepository memberCategoryRepository;
    private final MemberRepository memberRepository;

    public List<CategoryResponse> getCategories(Long memberId) {

        List<CategorySummary> summaries = categoryReader.findAll().categories();
        List<CategoryResponse> responses = new ArrayList<>();

        Set<Long> subscribedIds = new HashSet<>(memberCategoryRepository.findCategoryIdsByMemberId(memberId));

        for (CategorySummary summary : summaries) {
            boolean subscribed = subscribedIds.contains(summary.categoryId());
            responses.add(CategoryResponse.of(summary,  subscribed));
        }
        return responses;

    }

    @Transactional
    public void subscribe(Long memberId, Long categoryId){

        Category category = categoryRepository.findById(categoryId).orElse(null);
        if(category == null){
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        if(memberCategoryRepository.existsByMemberIdAndCategoryId(memberId, categoryId)){
            throw new BusinessException(ErrorCode.ALREADY_SUBSCRIBED);
        }

        Member member = memberRepository.getReferenceById(memberId);

        try {
            memberCategoryRepository.save(new MemberCategory(member, category));
        } catch (DataIntegrityViolationException  e) {
            if(isDuplicateSubscription(e)){
                throw new BusinessException(ErrorCode.ALREADY_SUBSCRIBED);
            }
            throw e;
        }

        log.info("카테고리 구독 memberId : {}, categoryId : {}", memberId, categoryId);
    }

    @Transactional
    public void unsubscribe(Long memberId, Long categoryId){
        int deletedCount = memberCategoryRepository.deletebyMemberIdAndCategoryId(memberId, categoryId);
        if(deletedCount == 0){
            throw new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND);
        }
        log.info("카테고리 구독 해제 memberId : {}, categoryId : {}", memberId, categoryId);
    }



    private boolean isDuplicateSubscription(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains("uk_member_category");
    }

}
