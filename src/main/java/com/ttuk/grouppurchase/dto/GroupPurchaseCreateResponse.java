package com.ttuk.grouppurchase.dto;

import com.ttuk.grouppurchase.GroupPurchase;
import com.ttuk.grouppurchase.GroupPurchaseStatus;

public record GroupPurchaseCreateResponse(
        Long groupPurchaseId,
        GroupPurchaseStatus status
) {

    public static GroupPurchaseCreateResponse from(GroupPurchase groupPurchase) {
        return new GroupPurchaseCreateResponse(groupPurchase.getId(), groupPurchase.getStatus());
    }

}
