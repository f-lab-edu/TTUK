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

    public int headcountOrZero() {
        return headcount == null ? 0 : headcount;
    }

    public int priceOr(int basePrice) {
        return price == null ? basePrice : price;
    }
}