package com.ttuk.participation;

import com.ttuk.global.response.PageResponse;
import com.ttuk.grouppurchase.GroupPurchaseStatus;
import com.ttuk.grouppurchase.redis.GroupPurchaseLiveReader;
import com.ttuk.grouppurchase.redis.GroupPurchaseLiveValue;
import com.ttuk.participation.dto.MyParticipationListRequest;
import com.ttuk.participation.dto.MyParticipationResponse;
import com.ttuk.participation.dto.MyParticipationRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ParticipationQueryService {

    private final ParticipationQueryRepository queryRepository;
    private final GroupPurchaseLiveReader liveReader;
    private final Clock clock;

    public PageResponse<MyParticipationResponse> findMyList(Long memberId, MyParticipationListRequest request) {

        LocalDateTime now = LocalDateTime.now(clock);
        List<MyParticipationRow> rows = queryRepository.findMyList(memberId, request, now);
        long totalElements = queryRepository.countMyList(memberId, request.status(), now);

        List<Long> liveIds = new ArrayList<>();
        for (MyParticipationRow row : rows) {
            if(needsLivePrice(row)) liveIds.add(row.groupPurchaseId());
        }
        Map<Long, GroupPurchaseLiveValue> liveValues = liveReader.findLiveValues(liveIds);

        List<MyParticipationResponse> content = new ArrayList<>(rows.size());
        for (MyParticipationRow row : rows) {
            content.add(toResponse(row, liveValues, now));
        }
        return PageResponse.of(content, request.page(), request.size(), totalElements);

    }

    private boolean needsLivePrice(MyParticipationRow row){
        return row.participationStatus() == ParticipationStatus.JOINED && row.groupPurchaseStatus() == GroupPurchaseStatus.OPEN;
    }

    private MyParticipationResponse toResponse(MyParticipationRow row, Map<Long, GroupPurchaseLiveValue> liveValues, LocalDateTime now){

        GroupPurchaseStatus displayed = row.groupPurchaseStatus().displayedAt(row.closeAt(), now);
        MyParticipationStatus status = MyParticipationStatus.of(row.participationStatus(), displayed);

        if(status == MyParticipationStatus.CANCELED){
             return toCanceledResponse(row);
        }
        if(row.groupPurchaseStatus() == GroupPurchaseStatus.CLOSED){
            int finalPrice = row.finalPrice();
            return MyParticipationResponse.settled(row, finalPrice, (long) finalPrice * row.quantity());
        }
        GroupPurchaseLiveValue live = liveValues.getOrDefault(row.groupPurchaseId(), GroupPurchaseLiveValue.EMPTY);
        return MyParticipationResponse.live(row, status, live.priceOr(row.basePrice()));

    }

    private MyParticipationResponse toCanceledResponse(MyParticipationRow row) {

        if(row.participationStatus() == ParticipationStatus.CANCELED){
            return MyParticipationResponse.canceled(row, row.participationCanceledBy(), null, row.participationCanceledAt());
        }
        return MyParticipationResponse.canceled(row, CanceledBy.SELLER, row.cancelReason(), row.groupPurchaseCanceledAt());
    }

}
