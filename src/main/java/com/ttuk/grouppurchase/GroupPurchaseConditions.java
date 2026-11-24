package com.ttuk.grouppurchase;

import com.querydsl.core.types.dsl.BooleanExpression;

import java.time.LocalDateTime;

import static com.ttuk.grouppurchase.QGroupPurchase.groupPurchase;

public final class GroupPurchaseConditions {

    private GroupPurchaseConditions() {

    }

    public static BooleanExpression isOpenAt(LocalDateTime now){
        return groupPurchase.status.eq(GroupPurchaseStatus.OPEN)
                .and(groupPurchase.closeAt.gt(now));
    }

    public static BooleanExpression isClosedAt(LocalDateTime now) {
        return groupPurchase.status.eq(GroupPurchaseStatus.CLOSED)
                .or(groupPurchase.status.eq(GroupPurchaseStatus.OPEN)
                        .and(groupPurchase.closeAt.loe(now)));
    }

    public static BooleanExpression isCanceled(){
        return groupPurchase.status.eq(GroupPurchaseStatus.CANCELED);
    }

    public static BooleanExpression isDisplayedAs(GroupPurchaseStatus status, LocalDateTime now){
        if(status == null) return null;

        return switch (status) {
            case OPEN -> isOpenAt(now);
            case CLOSED -> isClosedAt(now);
            case CANCELED ->  isCanceled();
        };

    }

}
