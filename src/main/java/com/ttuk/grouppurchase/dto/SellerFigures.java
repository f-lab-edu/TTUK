package com.ttuk.grouppurchase.dto;

import com.ttuk.grouppurchase.GroupPurchaseStatus;

public record SellerFigures(

        GroupPurchaseStatus status,
        int currentHeadcount,
        int currentPrice,
        Long totalQuantity,
        Long totalAmount

) {
}
