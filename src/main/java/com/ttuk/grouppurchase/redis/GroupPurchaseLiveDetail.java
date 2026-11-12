package com.ttuk.grouppurchase.redis;

import com.ttuk.grouppurchase.GroupPurchaseStatus;

public record GroupPurchaseLiveDetail(

        Integer headcount,
        Integer price,
        GroupPurchaseStatus status,
        boolean joined

) {

    public static GroupPurchaseLiveDetail of(String headcount, String price, String status, Boolean joined){

        return new GroupPurchaseLiveDetail(
                toInteger(headcount),
                toInteger(price),
                status == null ? null : GroupPurchaseStatus.valueOf(status),
                Boolean.TRUE.equals(joined));

    }

    private static Integer toInteger(String value){
        return value == null ? null : Integer.valueOf(value);
    }

}
