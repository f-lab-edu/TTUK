package com.ttuk.grouppurchase;

import com.ttuk.category.Category;
import com.ttuk.category.CategoryRepository;
import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import com.ttuk.global.storage.S3ImageStorage;
import com.ttuk.grouppurchase.GroupPurchaseImageValidator.ValidatedImage;
import com.ttuk.grouppurchase.dto.GroupPurchaseCancelRequest;
import com.ttuk.grouppurchase.dto.GroupPurchaseCancelResponse;
import com.ttuk.grouppurchase.dto.GroupPurchaseCreateRequest;
import com.ttuk.grouppurchase.dto.GroupPurchaseCreateResponse;
import com.ttuk.grouppurchase.event.GroupPurchaseCreatedEvent;
import com.ttuk.grouppurchase.redis.GroupPurchaseCancelOutcome;
import com.ttuk.grouppurchase.redis.GroupPurchaseCancelResult;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import com.ttuk.member.Member;
import com.ttuk.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
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
    private final Clock clock;
    private final GroupPurchaseRedisRepository groupPurchaseRedisRepository;

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

    public GroupPurchaseCancelResponse cancel(Long sellerId, Long groupPurchaseId, GroupPurchaseCancelRequest request) {

        Optional<GroupPurchase> found = groupPurchaseRepository.findById(groupPurchaseId);
        if(found.isEmpty()){
            throw new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_FOUND);
        }

        GroupPurchase groupPurchase = found.get();
        if(!groupPurchase.isSoldBy(sellerId)){
            throw new BusinessException(ErrorCode.NOT_GROUP_PURCHASE_OWNER);
        }

        if(groupPurchase.getStatus() != GroupPurchaseStatus.OPEN){
            throw new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_OPEN);
        }

        LocalDateTime now = LocalDateTime.now(clock);

        GroupPurchaseCancelResult result = groupPurchaseRedisRepository.cancelGroupPurchase(groupPurchaseId);
        if(result.outcome() == GroupPurchaseCancelOutcome.NOT_INITIALIZED){
            result = initializeAndRetryCancel(groupPurchase, now);
        }

        return switch (result.outcome()) {
            case CANCELED -> saveCanceled(groupPurchaseId, request.reason(), result, now);
            case ALREADY_CANCELED -> finishPendingCancel(groupPurchaseId, request.reason(), result, now);
            case NOT_OPEN -> throw new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_OPEN);
            case INVALID_STATE -> throw new IllegalStateException(
                    "Redis 인원·단가 값이 올바르지 않습니다. groupPurchaseId=" + groupPurchaseId);
            case NOT_INITIALIZED -> throw new IllegalStateException(
                    "초기화 후에도 Redis 키가 없습니다. groupPurchaseId=" + groupPurchaseId);
        };

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
        LocalDateTime now = LocalDateTime.now(clock);
        if (closeAt.isBefore(now.plus(MIN_OPEN_DURATION)) || closeAt.isAfter(now.plus(MAX_OPEN_DURATION))) {
            throw new BusinessException(ErrorCode.INVALID_CLOSE_AT);
        }
    }

    private GroupPurchaseCancelResult initializeAndRetryCancel(GroupPurchase groupPurchase, LocalDateTime now){

        if(!groupPurchase.isJoinableAt(now)){
                throw new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_OPEN);
        }

        groupPurchaseRedisRepository.initialize(groupPurchase);
        return groupPurchaseRedisRepository.cancelGroupPurchase(groupPurchase.getId());

    }

    private GroupPurchaseCancelResponse saveCanceled(Long groupPurchaseId, String reason, GroupPurchaseCancelResult result, LocalDateTime now) {

        int updated;

        try {
            updated = groupPurchaseRepository.cancelOpen(groupPurchaseId, reason, result.headcount(), result.price(), now);
        } catch (RuntimeException e) {
            reopenRedis(groupPurchaseId, e);
            throw e;
        }

        if(updated == 0){
            BusinessException notOpen = new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_OPEN);
            log.warn("판매자 취소: Redis는 OPEN이었는데 DB가 OPEN이 아님. groupPurchaseId={}", groupPurchaseId);
            reopenRedis(groupPurchaseId, notOpen);
            throw notOpen;
        }

        return cancelResponse(groupPurchaseId, reason, result, now);

    }

    private GroupPurchaseCancelResponse finishPendingCancel(Long groupPurchaseId, String reason, GroupPurchaseCancelResult result, LocalDateTime now) {

        if(groupPurchaseRepository.cancelOpen(groupPurchaseId, reason, result.headcount(), result.price(), now) == 0){
            throw new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_OPEN);
        }
        log.info("판매자 취소: Redis에서 이미 취소된 공동구매의 DB 기록을 마무리함. groupPurchaseId={}", groupPurchaseId);
        return cancelResponse(groupPurchaseId, reason, result, now);

    }

    private void reopenRedis(Long groupPurchaseId, RuntimeException cause){

        try {
            if(!groupPurchaseRedisRepository.reopenGroupPurchase(groupPurchaseId)){
                log.warn("판매자 취소 보상: 되돌릴 CANCELED 상태가 없음. groupPurchaseId={}", groupPurchaseId);
            }
        } catch (RuntimeException reopenFailure){
            cause.addSuppressed(reopenFailure);
            log.error("판매자 취소 보상 실패: Redis는 CANCELED, DB는 OPEN. 판매자가 다시 요청하면 마무리됨. groupPurchaseId={}", groupPurchaseId, reopenFailure);
        }

    }

    private GroupPurchaseCancelResponse cancelResponse(Long groupPurchaseId, String reason, GroupPurchaseCancelResult result, LocalDateTime now) {

        return new GroupPurchaseCancelResponse(groupPurchaseId, GroupPurchaseStatus.CANCELED, reason, now, result.participantCount());

    }
}
