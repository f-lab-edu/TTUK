package com.ttuk.grouppurchase.redis;

import com.ttuk.grouppurchase.GroupPurchaseStatus;

public record GroupPurchaseLiveDetail(
        Integer headcount,
        Integer price,
        GroupPurchaseStatus status,
        boolean joined
) {

    public static final GroupPurchaseLiveDetail EMPTY = new GroupPurchaseLiveDetail(null, null, null, false);

    public static GroupPurchaseLiveDetail of(String headcount, String price, String status, Boolean joined) {
        return new GroupPurchaseLiveDetail(
                RedisValueParser.toInteger(headcount),
                RedisValueParser.toInteger(price),
                RedisValueParser.toStatus(status),
                Boolean.TRUE.equals(joined));
    }

    public int headcountOrZero() {
        return headcount == null ? 0 : headcount;
    }

    public int priceOr(int basePrice) {
        return price == null ? basePrice : price;
    }

    public GroupPurchaseStatus statusOr(GroupPurchaseStatus dbStatus) {
        return status == null ? dbStatus : status;
    }
}