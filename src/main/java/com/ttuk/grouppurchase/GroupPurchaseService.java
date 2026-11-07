package com.ttuk.grouppurchase;

import com.ttuk.category.Category;
import com.ttuk.category.CategoryRepository;
import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import com.ttuk.global.storage.S3ImageStorage;
import com.ttuk.grouppurchase.GroupPurchaseImageValidator.ValidatedImage;
import com.ttuk.grouppurchase.dto.GroupPurchaseCreateRequest;
import com.ttuk.grouppurchase.dto.GroupPurchaseCreateResponse;
import com.ttuk.grouppurchase.event.GroupPurchaseCreatedEvent;
import com.ttuk.member.Member;
import com.ttuk.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupPurchaseService {

    private static final Duration MIN_OPEN_DURATION = Duration.ofHours(1);
    private static final Duration MAX_OPEN_DURATION = Duration.ofDays(7);

    private final GroupPurchaseRepository groupPurchaseRepository;
    private final GroupPurchaseImageRepository groupPurchaseImageRepository;
    private final CategoryRepository categoryRepository;
    private final MemberRepository memberRepository;
    private final GroupPurchaseImageValidator imageValidator;
    private final S3ImageStorage imageStorage;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public GroupPurchaseCreateResponse create(Long sellerId, GroupPurchaseCreateRequest request,
                                              List<MultipartFile> images) {
        validateDiscountRule(request);
        validateCloseAt(request.closeAt());
        if (!categoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        List<ValidatedImage> validatedImages = imageValidator.validate(images);

        List<String> imageKeys = uploadImages(validatedImages);
        try {
            return transactionTemplate.execute(status -> save(sellerId, request, imageKeys));
        } catch (RuntimeException e) {
            imageStorage.deleteAllQuietly(imageKeys);
            throw e;
        }
    }

    private List<String> uploadImages(List<ValidatedImage> images) {
        List<String> keys = new ArrayList<>(images.size());
        try {
            for (ValidatedImage image : images) {
                keys.add(imageStorage.upload(image.file(), image.type()));
            }
        } catch (RuntimeException e) {
            imageStorage.deleteAllQuietly(keys);
            throw e;
        }
        return keys;
    }

    private GroupPurchaseCreateResponse save(Long sellerId, GroupPurchaseCreateRequest request,
                                             List<String> imageKeys) {
        Member seller = memberRepository.getReferenceById(sellerId);
        Category category = categoryRepository.getReferenceById(request.categoryId());

        GroupPurchase saved = groupPurchaseRepository.save(GroupPurchase.builder()
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
                .build());

        List<GroupPurchaseImage> images = new ArrayList<>(imageKeys.size());
        for (int order = 0; order < imageKeys.size(); order++) {
            images.add(GroupPurchaseImage.builder()
                    .groupPurchase(saved)
                    .imageKey(imageKeys.get(order))
                    .sortOrder(order)
                    .build());
        }
        groupPurchaseImageRepository.saveAll(images);

        eventPublisher.publishEvent(GroupPurchaseCreatedEvent.from(saved));
        return GroupPurchaseCreateResponse.from(saved, imageStorage.toUrl(imageKeys.get(0)));
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
        LocalDateTime now = LocalDateTime.now();
        if (closeAt.isBefore(now.plus(MIN_OPEN_DURATION)) || closeAt.isAfter(now.plus(MAX_OPEN_DURATION))) {
            throw new BusinessException(ErrorCode.INVALID_CLOSE_AT);
        }
    }
}