package com.ttuk.grouppurchase;

import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import com.ttuk.global.response.PageResponse;
import com.ttuk.global.storage.S3ImageStorage;
import com.ttuk.grouppurchase.dto.*;
import com.ttuk.grouppurchase.redis.GroupPurchaseLiveDetail;
import com.ttuk.grouppurchase.redis.GroupPurchaseLiveValue;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import com.ttuk.participation.ParticipationRepository;
import com.ttuk.participation.dto.JoinedQuantity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GroupPurchaseQueryService {

    private final GroupPurchaseQueryRepository queryRepository;
    private final GroupPurchaseRedisRepository redisRepository;
    private final S3ImageStorage imageStorage;
    private final Clock clock;
    private final ParticipationRepository participationRepository;

    public PageResponse<GroupPurchaseSummaryResponse> findOpenList(GroupPurchaseListRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<GroupPurchaseListRow> rows = queryRepository.findOpenList(request, now);
        long totalElements = queryRepository.countOpen(request.categoryId(), now);

        List<Long> ids = new ArrayList<>(rows.size());
        for (GroupPurchaseListRow row : rows) {
            ids.add(row.groupPurchaseId());
        }
        Map<Long, GroupPurchaseLiveValue> liveValues = findLiveValues(ids);

        List<GroupPurchaseSummaryResponse> content = new ArrayList<>(rows.size());
        for (GroupPurchaseListRow row : rows) {
            GroupPurchaseLiveValue live = liveValues.getOrDefault(row.groupPurchaseId(), GroupPurchaseLiveValue.EMPTY);
            String thumbnailUrl = row.thumbnailKey() == null ? null : imageStorage.toUrl(row.thumbnailKey());
            content.add(GroupPurchaseSummaryResponse.of(
                    row, live.headcountOrZero(), live.priceOr(row.basePrice()), thumbnailUrl));
        }

        return PageResponse.of(content, request.page(), request.size(), totalElements);
    }

    public GroupPurchaseDetailResponse findDetail(Long groupPurchaseId, Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<GroupPurchaseDetailRow> found = queryRepository.findDetail(groupPurchaseId);
        if (found.isEmpty()) {
            throw new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_FOUND);
        }
        GroupPurchaseDetailRow row = found.get();
        List<String> imageUrls = new ArrayList<>();
        for (String imageKey : queryRepository.findImageKeys(groupPurchaseId)) {
            imageUrls.add(imageStorage.toUrl(imageKey));
        }

        GroupPurchaseLiveDetail live = findLiveDetail(groupPurchaseId, memberId);
        int currentHeadcount = live.headcountOrZero();
        GroupPurchaseStatus status = displayStatus(live.statusOr(row.status()), row.closeAt(), now);

        Optional<NextDiscount> next = findNextDiscount(row, currentHeadcount, status);
        Integer nextDiscountIn = null;
        Integer nextPrice = null;
        if (next.isPresent()) {
            nextDiscountIn = next.get().remainingHeadcount();
            nextPrice = next.get().price();
        }

        return GroupPurchaseDetailResponse.of(
                row, imageUrls, currentHeadcount, live.priceOr(row.basePrice()),
                nextDiscountIn, nextPrice, status, live.joined());
    }

    public PageResponse<SellerGroupPurchaseResponse> findSellerList(Long sellerId, SellerGroupPurchaseListRequest request) {

        LocalDateTime now = LocalDateTime.now(clock);
        List<SellerGroupPurchaseRow> rows = queryRepository.findSellerList(sellerId, request, now);
        long totalElements = queryRepository.countSellerList(sellerId, request.status(), now);

        Map<Long, SellerFigures> figures = findSellerFigures(rows, now);

        List<SellerGroupPurchaseResponse> content = new ArrayList<>(rows.size());
        for (SellerGroupPurchaseRow row : rows) {
            content.add(SellerGroupPurchaseResponse.of(row, figures.get(row.groupPurchaseId())));
        }

        return PageResponse.of(content, request.page(), request.size(), totalElements);

    }

    private Optional<NextDiscount> findNextDiscount(GroupPurchaseDetailRow row,
                                                    int currentHeadcount,
                                                    GroupPurchaseStatus status) {
        if (status != GroupPurchaseStatus.OPEN) {
            return Optional.empty();
        }
        return row.discountPolicy().nextDiscount(currentHeadcount, row.maxHeadcount());
    }

    private GroupPurchaseStatus displayStatus(GroupPurchaseStatus status, LocalDateTime closeAt, LocalDateTime now) {
        if (status == GroupPurchaseStatus.OPEN && !now.isBefore(closeAt)) {
            return GroupPurchaseStatus.CLOSED;
        }
        return status;
    }

    private Map<Long, GroupPurchaseLiveValue> findLiveValues(List<Long> ids) {
        try {
            return redisRepository.findLiveValues(ids);
        } catch (DataAccessException e) {
            log.error("목록 Redis 조회 실패: 인원·단가를 DB 값으로 대신함. ids={}", ids, e);
            return Map.of();
        }
    }

    private GroupPurchaseLiveDetail findLiveDetail(Long groupPurchaseId, Long memberId) {
        try {
            return redisRepository.findLiveDetail(groupPurchaseId, memberId);
        } catch (DataAccessException e) {
            log.error("상세 Redis 조회 실패: 인원·단가·상태를 DB 값으로 대신함. groupPurchaseId={}",
                    groupPurchaseId, e);
            return GroupPurchaseLiveDetail.EMPTY;
        }
    }

    private Map<Long, SellerFigures> findSellerFigures(List<? extends SellerFigureSource> sources, LocalDateTime now) {

        List<Long> openIds = new ArrayList<>();
        List<Long> soldIds = new ArrayList<>();

        for(SellerFigureSource source : sources) {
            if(source.status() == GroupPurchaseStatus.CANCELED){
                continue;
            }
            soldIds.add(source.groupPurchaseId());
            if(source.status() == GroupPurchaseStatus.OPEN) {
                openIds.add(source.groupPurchaseId());
            }
        }

        Map<Long, GroupPurchaseLiveValue> liveValues = openIds.isEmpty() ? Map.of() : findLiveValues(openIds);
        Map<Long, Long> quantities = findJoinedQuantities(soldIds);

        Map<Long, SellerFigures> figures = new HashMap<>();
        for(SellerFigureSource source : sources) {
            figures.put(source.groupPurchaseId(), toFigures(source, liveValues, quantities, now));
        }

        return figures;
    }

    private SellerFigures toFigures(SellerFigureSource source, Map<Long, GroupPurchaseLiveValue> liveValues,
                                    Map<Long, Long> quantities, LocalDateTime now) {

        GroupPurchaseStatus status = displayStatus(source.status(), source.closeAt(), now);
        if(source.status() == GroupPurchaseStatus.CANCELED){
            return new SellerFigures(status, source.finalHeadcount(), source.finalPrice(), null, null);
        }

        Long totalQuantity = quantities.getOrDefault(source.groupPurchaseId(), 0L);
        if(source.status() == GroupPurchaseStatus.CLOSED){
            long totalAmount = totalQuantity * source.finalPrice();
            return new SellerFigures(status, source.finalHeadcount(), source.finalPrice(), totalQuantity, totalAmount);
        }

        GroupPurchaseLiveValue live = liveValues.getOrDefault(source.groupPurchaseId(), GroupPurchaseLiveValue.EMPTY);
        return new SellerFigures(status, live.headcountOrZero(), live.priceOr(source.basePrice()), totalQuantity, null);

    }

    private Map<Long, Long> findJoinedQuantities(List<Long> groupPurchaseIds) {

        Map<Long, Long> quantities = new HashMap<>();
        if(groupPurchaseIds.isEmpty()){
            return quantities;
        }

        for (JoinedQuantity joined : participationRepository.sumJoinedQuantities(groupPurchaseIds)) {
            quantities.put(joined.groupPurchaseId(), joined.totalQuantity());
        }
        return quantities;

    }

}