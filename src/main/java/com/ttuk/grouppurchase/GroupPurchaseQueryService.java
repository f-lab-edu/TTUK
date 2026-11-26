package com.ttuk.grouppurchase;

import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import com.ttuk.global.response.PageResponse;
import com.ttuk.global.storage.S3ImageStorage;
import com.ttuk.grouppurchase.dto.*;
import com.ttuk.grouppurchase.redis.GroupPurchaseLiveDetail;
import com.ttuk.grouppurchase.redis.GroupPurchaseLiveReader;
import com.ttuk.grouppurchase.redis.GroupPurchaseLiveValue;
import com.ttuk.participation.ParticipationRepository;
import com.ttuk.participation.dto.JoinedQuantity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GroupPurchaseQueryService {

    private final GroupPurchaseQueryRepository queryRepository;
    private final GroupPurchaseLiveReader liveReader;
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
        Map<Long, GroupPurchaseLiveValue> liveValues = liveReader.findLiveValues(ids);

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

        GroupPurchaseLiveDetail live = liveReader.findLiveDetail(groupPurchaseId, memberId);
        int currentHeadcount = live.headcountOrZero();
        GroupPurchaseStatus status = live.statusOr(row.status()).displayedAt(row.closeAt(), now);

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

    public SellerGroupPurchaseDetailResponse findSellerDetail(Long groupPurchaseId, Long sellerId) {

        LocalDateTime now = LocalDateTime.now(clock);
        Optional<SellerGroupPurchaseDetailRow> found = queryRepository.findSellerDetail(groupPurchaseId);
        if(found.isEmpty()) {
            throw new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_FOUND);
        }
        SellerGroupPurchaseDetailRow row = found.get();
        if(!row.isSoldBy(sellerId)) {
            throw new BusinessException(ErrorCode.NOT_GROUP_PURCHASE_OWNER);
        }

        SellerFigures figures = findSellerFigures(List.of(row), now).get(row.groupPurchaseId());
        return SellerGroupPurchaseDetailResponse.of(row, figures);

    }

    private Optional<NextDiscount> findNextDiscount(GroupPurchaseDetailRow row,
                                                    int currentHeadcount,
                                                    GroupPurchaseStatus status) {
        if (status != GroupPurchaseStatus.OPEN) {
            return Optional.empty();
        }
        return row.discountPolicy().nextDiscount(currentHeadcount, row.maxHeadcount());
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

        Map<Long, GroupPurchaseLiveValue> liveValues = liveReader.findLiveValues(openIds);
        Map<Long, Long> quantities = findJoinedQuantities(soldIds);

        Map<Long, SellerFigures> figures = new HashMap<>();
        for(SellerFigureSource source : sources) {
            figures.put(source.groupPurchaseId(), toFigures(source, liveValues, quantities, now));
        }

        return figures;
    }

    private SellerFigures toFigures(SellerFigureSource source, Map<Long, GroupPurchaseLiveValue> liveValues,
                                    Map<Long, Long> quantities, LocalDateTime now) {

        GroupPurchaseStatus status = source.status().displayedAt(source.closeAt(), now);
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