package com.ttuk.grouppurchase;

import com.ttuk.global.response.PageResponse;
import com.ttuk.global.storage.S3ImageStorage;
import com.ttuk.grouppurchase.dto.GroupPurchaseListRequest;
import com.ttuk.grouppurchase.dto.GroupPurchaseListRow;
import com.ttuk.grouppurchase.dto.GroupPurchaseSummaryResponse;
import com.ttuk.grouppurchase.redis.GroupPurchaseLiveValue;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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


}
