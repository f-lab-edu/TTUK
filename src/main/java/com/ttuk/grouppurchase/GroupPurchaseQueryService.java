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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GroupPurchaseQueryService {


    private final GroupPurchaseQueryRepository queryRepository;
    private final GroupPurchaseRedisRepository redisRepository;
    private final S3ImageStorage imageStorage;

    public PageResponse<GroupPurchaseSummaryResponse> findOpenList(GroupPurchaseListRequest request){

        List<GroupPurchaseListRow> rows = queryRepository.findopenList(request);
        long totalElement = queryRepository.countOpen(request.categoryId());

        List<Long> ids = new ArrayList<>(rows.size());
        for(GroupPurchaseListRow row : rows){
            ids.add(row.groupPurchaseId());
        }

        Map<Long, GroupPurchaseLiveValue> liveValues = redisRepository.findLiveValues(ids);

        List<GroupPurchaseSummaryResponse> content = new ArrayList<>(rows.size());
        for(GroupPurchaseListRow row : rows){
            GroupPurchaseLiveValue live = liveValues.get(row.groupPurchaseId());
            int currentHeadCount = live.headcount() == null ? 0 : live.headcount();
            int currentPrice = live.price() == null ? row.basePrice() : live.price();
            String thumbnailUrl = row.thumbnailKey() == null ? null : imageStorage.toUrl(row.thumbnailKey());

            content.add(GroupPurchaseSummaryResponse.of(row, currentHeadCount, currentPrice, thumbnailUrl));

        }
        return PageResponse.of(content, request.page(), request.size(), totalElement);

    }

    public GroupPurchaseDetailResponse findDetail(Long groupPurchaseId, Long memberId){

        Optional<GroupPurchaseDetailRow> found = queryRepository.findDetail(groupPurchaseId);
        if(found.isEmpty()){
            throw new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_FOUND);
        }
        GroupPurchaseDetailRow row = found.get();
        List<String> imageUrls = new ArrayList<>();
        for(String imageKey : queryRepository.findImageKeys(groupPurchaseId)){
            imageUrls.add(imageStorage.toUrl(imageKey));
        }

        GroupPurchaseLiveDetail live = redisRepository.findLiveDetail(groupPurchaseId, memberId);
        int currentHeadcount = live.headcount() == null ? 0 : live.headcount();
        int currentPrice = live.price() == null ? row.basePrice() : live.price();
        GroupPurchaseStatus status = live.status() == null ? row.status() : live.status();

        return GroupPurchaseDetailResponse.of(
                row, imageUrls, currentHeadcount, currentPrice, status, live.joined());

    }


}
