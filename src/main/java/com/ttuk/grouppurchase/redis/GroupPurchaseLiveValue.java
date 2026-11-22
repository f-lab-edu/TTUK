package com.ttuk.grouppurchase.redis;

public record GroupPurchaseLiveValue(
        Integer headcount,
        Integer price
) {

    public static final GroupPurchaseLiveValue EMPTY = new GroupPurchaseLiveValue(null, null);

    public static GroupPurchaseLiveValue of(String headcount, String price) {
        return new GroupPurchaseLiveValue(
                RedisValueParser.toInteger(headcount),
                RedisValueParser.toInteger(price));
    }
}