package com.ttuk.grouppurchase.dto;

import com.ttuk.grouppurchase.GroupPurchaseStatus;

import java.time.LocalDateTime;

public record GroupPurchaseListRow(

        Long groupPurchaseId,
        String title,
        String categoryName,
        int basePrice,
        Integer maxHeadcount,
        LocalDateTime closeAt,
        GroupPurchaseStatus status,
        String thumbnailKey


) {
}
