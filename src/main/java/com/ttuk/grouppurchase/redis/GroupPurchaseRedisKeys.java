package com.ttuk.grouppurchase.redis;

public final class GroupPurchaseRedisKeys {

    private GroupPurchaseRedisKeys() {
    }

    public static String meta(Long groupPurchaseId) {
        return key(groupPurchaseId, "meta");
    }

    public static String headcount(Long groupPurchaseId) {
        return key(groupPurchaseId, "headcount");
    }

    public static String price(Long groupPurchaseId) {
        return key(groupPurchaseId, "price");
    }

    public static String participants(Long groupPurchaseId) {
        return key(groupPurchaseId, "participants");
    }

    public static String status(Long groupPurchaseId) {
        return key(groupPurchaseId, "status");
    }

    private static String key(Long groupPurchaseId, String field) {
        return "group_purchase:{" + groupPurchaseId + "}:" + field;
    }
}