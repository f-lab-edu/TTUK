package com.ttuk.grouppurchase;

import com.ttuk.category.Category;
import com.ttuk.category.CategoryRepository;
import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import com.ttuk.grouppurchase.dto.GroupPurchaseCreateRequest;
import com.ttuk.grouppurchase.dto.GroupPurchaseCreateResponse;
import com.ttuk.member.Member;
import com.ttuk.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class GroupPurchaseService {

    private static final Duration MIN_OPEN_DURATION = Duration.ofHours(1);
    private static final Duration MAX_OPEN_DURATION = Duration.ofDays(7);

    private final GroupPurchaseRepository groupPurchaseRepository;
    private final CategoryRepository categoryRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    @Transactional
    public GroupPurchaseCreateResponse create(Long sellerId, GroupPurchaseCreateRequest request) {
        validateDiscountRule(request);
        validateCloseAt(request.closeAt());

        if (!categoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        Member seller = memberRepository.getReferenceById(sellerId);
        Category category = categoryRepository.getReferenceById(request.categoryId());

        GroupPurchase groupPurchase = GroupPurchase.builder()
                .seller(seller)
                .category(category)
                .title(request.title())
                .description(request.description())
                .unit(request.unit())
                .basePrice(request.basePrice())
                .floorPrice(request.floorPrice())
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .discountStepSize(request.discountStepSize())
                .maxHeadcount(request.maxHeadcount())
                .closeAt(request.closeAt())
                .build();

        GroupPurchase saved = groupPurchaseRepository.save(groupPurchase);
        return GroupPurchaseCreateResponse.from(saved);
    }

    private void validateDiscountRule(GroupPurchaseCreateRequest request) {
        if (request.floorPrice() > request.basePrice()) {
            throw new BusinessException(ErrorCode.INVALID_DISCOUNT_RULE);
        }
        if (!request.discountType().isValidValue(request.discountValue(), request.basePrice())) {
            throw new BusinessException(ErrorCode.INVALID_DISCOUNT_RULE);
        }
        if (request.maxHeadcount() != null && request.maxHeadcount() < request.discountStepSize()) {
            throw new BusinessException(ErrorCode.INVALID_DISCOUNT_RULE);
        }
    }

    private void validateCloseAt(LocalDateTime closeAt) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (closeAt.isBefore(now.plus(MIN_OPEN_DURATION)) || closeAt.isAfter(now.plus(MAX_OPEN_DURATION))) {
            throw new BusinessException(ErrorCode.INVALID_CLOSE_AT);
        }
    }
}
