package com.ttuk.grouppurchase.dto;

import com.ttuk.grouppurchase.GroupPurchaseStatus;

import java.time.LocalDateTime;

public record  SellerGroupPurchaseRow(

        Long groupPurchaseId,
        String title,
        GroupPurchaseStatus status,
        int basePrice,
        Integer maxHeadcount,
        Integer finalHeadcount,
        Integer finalPrice,
        LocalDateTime closeAt,
        LocalDateTime createdAt

) {
}
