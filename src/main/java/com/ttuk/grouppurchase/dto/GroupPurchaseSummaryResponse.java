package com.ttuk.grouppurchase.dto;

import com.ttuk.grouppurchase.GroupPurchaseStatus;

import java.time.LocalDateTime;

public record GroupPurchaseSummaryResponse(

        Long groupPurchaseId,
        String title,
        String categoryName,
        int basePrice,
        int currentPrice,
        int currentHeadcount,
        Integer maxHeadcount,
        LocalDateTime closeAt,
        GroupPurchaseStatus status,
        String thumbnailUrl

) {

    public static GroupPurchaseSummaryResponse of(GroupPurchaseListRow row, int currentHeadcount,
                                                  int currentPrice, String thumbnailUrl) {
        return new GroupPurchaseSummaryResponse(
                row.groupPurchaseId(),
                row.title(),
                row.categoryName(),
                row.basePrice(),
                currentPrice,
                currentHeadcount,
                row.maxHeadcount(),
                row.closeAt(),
                row.status(),
                thumbnailUrl
        );

    }
}
