package com.ttuk.grouppurchase.dto;

import com.ttuk.grouppurchase.DiscountType;
import com.ttuk.grouppurchase.GroupPurchaseStatus;

import java.time.LocalDateTime;

public record SellerGroupPurchaseDetailRow(

        Long groupPurchaseId,
        Long sellerId,
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
        Integer finalHeadcount,
        Integer finalPrice,
        LocalDateTime closeAt,
        LocalDateTime createdAt,
        LocalDateTime closedAt,
        String cancelReason,
        LocalDateTime canceledAt

) implements SellerFigureSource {

    public boolean isSoldBy(Long memberId){
        return sellerId.equals(memberId);
    }

}
