package com.ttuk.grouppurchase.event;

import com.ttuk.grouppurchase.DiscountType;
import com.ttuk.grouppurchase.GroupPurchase;

import java.time.LocalDateTime;

public record GroupPurchaseCreatedEvent(
        Long groupPurchaseId,
        int basePrice,
        int floorPrice,
        DiscountType discountType,
        int discountValue,
        int discountStepSize,
        Integer maxHeadcount,
        LocalDateTime closeAt
) {
    public static GroupPurchaseCreatedEvent from(GroupPurchase groupPurchase) {
        return new GroupPurchaseCreatedEvent(
                groupPurchase.getId(),
                groupPurchase.getBasePrice(),
                groupPurchase.getFloorPrice(),
                groupPurchase.getDiscountType(),
                groupPurchase.getDiscountValue(),
                groupPurchase.getDiscountStepSize(),
                groupPurchase.getMaxHeadcount(),
                groupPurchase.getCloseAt()
        );
    }
}
