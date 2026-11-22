package com.ttuk.grouppurchase.dto;

import com.ttuk.grouppurchase.GroupPurchaseStatus;

import java.time.LocalDateTime;

public record SellerGroupPurchaseResponse(

        Long groupPurchaseId,
        String title,
        GroupPurchaseStatus status,
        int currentHeadcount,
        Integer maxHeadcount,
        int currentPrice,
        Long totalQuantity,
        Long totalAmount,
        LocalDateTime closeAt,
        LocalDateTime createdAt

) {

    public static SellerGroupPurchaseResponse of(SellerGroupPurchaseRow row, GroupPurchaseStatus status, int currentHeadcount, int currentPrice,
                                                 Long totalQuantity, Long totalAmount) {

        return new SellerGroupPurchaseResponse(
                row.groupPurchaseId(),
                row.title(),
                status,
                currentHeadcount,
                row.maxHeadcount(),
                currentPrice,
                totalQuantity,
                totalAmount,
                row.closeAt(),
                row.createdAt());

    }

}
