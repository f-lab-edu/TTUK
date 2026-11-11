package com.ttuk.grouppurchase.redis;

public record GroupPurchaseLiveValue(

        Integer headcount,
        Integer price

) {

    public static GroupPurchaseLiveValue of(String headcount, String price){
        return new GroupPurchaseLiveValue(toInteger(headcount), toInteger(price));
    }

    private static Integer toInteger(String value){
        return value == null ? null : Integer.valueOf(value);
    }

}
