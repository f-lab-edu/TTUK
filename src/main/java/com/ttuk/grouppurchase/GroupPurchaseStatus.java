package com.ttuk.grouppurchase;

import java.time.LocalDateTime;

public enum GroupPurchaseStatus {

    OPEN,
    CLOSED,
    CANCELED;

    public GroupPurchaseStatus displayedAt(LocalDateTime closeAt, LocalDateTime now) {
        if (this == OPEN && !now.isBefore(closeAt)) {
            return CLOSED;
        }
        return this;
    }

}
