package com.ttuk.grouppurchase.dto;

import com.ttuk.grouppurchase.DiscountPolicy;
import com.ttuk.grouppurchase.DiscountType;
import com.ttuk.grouppurchase.GroupPurchaseStatus;

import java.time.LocalDateTime;

public record GroupPurchaseDetailRow(
        Long groupPurchaseId,
        String title,
        String description,
        String unit,
        String categoryName,
        String sellerNickname,
        int basePrice,
        int floorPrice,
        DiscountType discountType,
        int discountValue,
        int discountStepSize,
        Integer maxHeadcount,
        LocalDateTime closeAt,
        GroupPurchaseStatus status
) {

    public DiscountPolicy discountPolicy() {
        return new DiscountPolicy(basePrice, floorPrice, discountType, discountValue, discountStepSize);
    }

}
