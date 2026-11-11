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
        Integer maxHeadcount,
        LocalDateTime closeAt,
        GroupPurchaseStatus status
) {
    public static GroupPurchaseDetailResponse of(GroupPurchaseDetailRow row, List<String> imageUrls) {
        return new GroupPurchaseDetailResponse(
                row.groupPurchaseId(), row.title(), row.description(), row.unit(),
                row.categoryName(), row.sellerNickname(), imageUrls,
                row.basePrice(), row.floorPrice(),
                row.discountType(), row.discountValue(), row.discountStepSize(),
                row.maxHeadcount(), row.closeAt(), row.status());
    }
}
