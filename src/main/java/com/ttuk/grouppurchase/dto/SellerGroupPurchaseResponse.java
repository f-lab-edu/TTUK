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

    public static SellerGroupPurchaseResponse of(SellerGroupPurchaseRow row, SellerFigures figures) {

        return new SellerGroupPurchaseResponse(
                row.groupPurchaseId(),
                row.title(),
                figures.status(),
                figures.currentHeadcount(),
                row.maxHeadcount(),
                figures.currentPrice(),
                figures.totalQuantity(),
                figures.totalAmount(),
                row.closeAt(),
                row.createdAt());

    }

}
