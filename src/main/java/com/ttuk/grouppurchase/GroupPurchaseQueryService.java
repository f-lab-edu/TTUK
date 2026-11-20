package com.ttuk.grouppurchase;

import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import com.ttuk.global.response.PageResponse;
import com.ttuk.global.storage.S3ImageStorage;
import com.ttuk.grouppurchase.dto.*;
import com.ttuk.grouppurchase.redis.GroupPurchaseLiveDetail;
import com.ttuk.grouppurchase.redis.GroupPurchaseLiveValue;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
            int currentHeadcount = live.headcount() == null ? 0 : live.headcount();
            int currentPrice = live.price() == null ? row.basePrice() : live.price();
            String thumbnailUrl = row.thumbnailKey() == null ? null : imageStorage.toUrl(row.thumbnailKey());
            content.add(GroupPurchaseSummaryResponse.of(row, currentHeadcount, currentPrice, thumbnailUrl));
        }

        return PageResponse.of(content, request.page(), request.size(), totalElements);
    }

    public GroupPurchaseDetailResponse findDetail(Long groupPurchaseId, Long memberId) {
        Optional<GroupPurchaseDetailRow> found = queryRepository.findDetail(groupPurchaseId);
        if (found.isEmpty()) {
            throw new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_FOUND);
        }
        GroupPurchaseDetailRow row = found.get();
        List<String> imageUrls = new ArrayList<>();
        for (String imageKey : queryRepository.findImageKeys(groupPurchaseId)) {
            imageUrls.add(imageStorage.toUrl(imageKey));
        }

        GroupPurchaseLiveDetail live = redisRepository.findLiveDetail(groupPurchaseId, memberId);
        int currentHeadcount = live.headcount() == null ? 0 : live.headcount();
        int currentPrice = live.price() == null ? row.basePrice() : live.price();
        GroupPurchaseStatus status = live.status() == null ? row.status() : live.status();

        return GroupPurchaseDetailResponse.of(
                row, imageUrls, currentHeadcount, currentPrice, status, live.joined());
    }

    private Map<Long, GroupPurchaseLiveValue> findLiveValues(List<Long> ids) {
        try {
            return redisRepository.findLiveValues(ids);
        } catch (DataAccessException e) {
            log.error("목록 Redis 조회 실패: 인원·단가를 DB 값으로 대신함. ids={}", ids, e);
            return Map.of();
        }
    }
}