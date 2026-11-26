package com.ttuk.grouppurchase.dto;

import com.ttuk.grouppurchase.GroupPurchaseStatus;

import java.time.LocalDateTime;

public record GroupPurchaseCancelResponse (

        Long groupPurchaseId,
        GroupPurchaseStatus status,
        String cancelReason,
        LocalDateTime canceledAt,
        int notifiedCount

) {
}
