package com.ttuk.grouppurchase.redis;

public final class GroupPurchaseRedisKeys {

    private static final String PREFIX = "group_purchase:";

    public GroupPurchaseRedisKeys() {
    }

    public static String meta(Long groupPurchaseId){
        return PREFIX + groupPurchaseId + ":meta";
    }

    public static String headcount(Long groupPurchaseId){
        return PREFIX + groupPurchaseId + ":headcount";
    }

    public static String price(Long groupPurchaseId) {
        return PREFIX + groupPurchaseId + ":price";
    }

    public static String participants(Long groupPurchaseId) {
        return PREFIX + groupPurchaseId + ":participants";
    }

    public static String status(Long groupPurchaseId) {
        return PREFIX + groupPurchaseId + ":status";
    }
}
