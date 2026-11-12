package com.ttuk.grouppurchase.dto;

import com.ttuk.grouppurchase.DiscountType;
import com.ttuk.grouppurchase.GroupPurchaseStatus;

import java.time.LocalDateTime;
import java.util.List;

public record GroupPurchaseDetailResponse(
        Long groupPurchaseId,
        String title,
        String description,
        String unit,
        String categoryName,
        String sellerNickname,
        List<String> imageUrls,
        int basePrice,
        int floorPrice,
        DiscountType discountType,
        int discountValue,
        int discountStepSize,
        int currentHeadcount,
        int currentPrice,
        Integer nextDiscountIn,
        Integer nextPrice,
        Integer maxHeadcount,
        LocalDateTime closeAt,
        GroupPurchaseStatus status,
        boolean joined
) {
    public static GroupPurchaseDetailResponse of(GroupPurchaseDetailRow row, List<String> imageUrls,
                                                 int currentHeadcount,
                                                 int currentPrice,
                                                 Integer nextDiscountIn,
                                                 Integer nextPrice,
                                                 GroupPurchaseStatus status,
                                                 boolean joined) {

        return new GroupPurchaseDetailResponse(
                row.groupPurchaseId(), row.title(), row.description(), row.unit(),
                row.categoryName(), row.sellerNickname(), imageUrls,
                row.basePrice(), row.floorPrice(),
                row.discountType(), row.discountValue(), row.discountStepSize(),
                currentHeadcount, currentPrice, nextDiscountIn, nextPrice,
                row.maxHeadcount(), row.closeAt(), status, joined);
        
    }
}