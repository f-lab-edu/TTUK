package com.ttuk.grouppurchase;

import com.ttuk.global.response.PageResponse;
import com.ttuk.global.storage.S3ImageStorage;
import com.ttuk.grouppurchase.dto.GroupPurchaseListRequest;
import com.ttuk.grouppurchase.dto.GroupPurchaseListRow;
import com.ttuk.grouppurchase.dto.GroupPurchaseSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GroupPurchaseQueryService {

    private static final int NO_PARTICIPANTS = 0;

    private final GroupPurchaseQueryRepository queryRepository;
    private final S3ImageStorage imageStorage;
    private final Clock clock;

    public PageResponse<GroupPurchaseSummaryResponse> findOpenList(GroupPurchaseListRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<GroupPurchaseListRow> rows = queryRepository.findOpenList(request, now);
        long totalElements = queryRepository.countOpen(request.categoryId(), now);

        List<GroupPurchaseSummaryResponse> content = new ArrayList<>(rows.size());
        for (GroupPurchaseListRow row : rows) {
            String thumbnailUrl = row.thumbnailKey() == null ? null : imageStorage.toUrl(row.thumbnailKey());
            content.add(GroupPurchaseSummaryResponse.of(row, NO_PARTICIPANTS, row.basePrice(), thumbnailUrl));
        }

        return PageResponse.of(content, request.page(), request.size(), totalElements);
    }
}