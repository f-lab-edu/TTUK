package com.ttuk.grouppurchase.dto;

import com.ttuk.grouppurchase.DiscountType;
import com.ttuk.grouppurchase.GroupPurchaseStatus;

import java.time.LocalDateTime;

public record SellerGroupPurchaseDetailResponse(

        Long groupPurchaseId,
        String title,
        String categoryName,
        String unit,
        GroupPurchaseStatus status,
        int basePrice,
        int floorPrice,
        DiscountType discountType,
        int discountValue,
        int discountStepSize,
        Integer maxHeadcount,
        int currentHeadcount,
        int currentPrice,
        Long totalQuantity,
        Long totalAmount,
        LocalDateTime closeAt,
        LocalDateTime createdAt,
        LocalDateTime closedAt,
        String cancelReason,
        LocalDateTime canceledAt

) {

    public static SellerGroupPurchaseDetailResponse of(SellerGroupPurchaseDetailRow row, SellerFigures figures){

        return new SellerGroupPurchaseDetailResponse(
                row.groupPurchaseId(),
                row.title(),
                row.categoryName(),
                row.unit(),
                figures.status(),
                row.basePrice(),
                row.floorPrice(),
                row.discountType(),
                row.discountValue(),
                row.discountStepSize(),
                row.maxHeadcount(),
                figures.currentHeadcount(),
                figures.currentPrice(),
                figures.totalQuantity(),
                figures.totalAmount(),
                row.closeAt(),
                row.createdAt(),
                row.closedAt(),
                row.cancelReason(),
                row.canceledAt());

    }

}
