package com.ttuk.grouppurchase.redis;

import com.ttuk.global.config.ClockConfig;
import com.ttuk.grouppurchase.DiscountPolicy;
import com.ttuk.grouppurchase.DiscountType;
import com.ttuk.grouppurchase.GroupPurchaseStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record GroupPurchaseLiveState(

        long version,
        GroupPurchaseStatus status,
        Integer headcount,
        Integer price,
        DiscountPolicy discountPolicy,
        Integer maxHeadcount,
        LocalDateTime closeAt

) {

    private static final int VERSION = 0;
    private static final int STATUS = 1;
    private static final int HEADCOUNT = 2;
    private static final int PRICE = 3;
    private static final int BASE_PRICE = 4;
    private static final int FLOOR_PRICE = 5;
    private static final int DISCOUNT_TYPE = 6;
    private static final int DISCOUNT_VALUE = 7;
    private static final int DISCOUNT_STEP_SIZE = 8;
    private static final int MAX_HEADCOUNT = 9;
    private static final int CLOSE_AT = 10;

    public static GroupPurchaseLiveState from(Long groupPurchaseId, List<String> scriptResult) {

        String basePrice = scriptResult.get(BASE_PRICE);
        String floorPrice = scriptResult.get(FLOOR_PRICE);
        String discountType = scriptResult.get(DISCOUNT_TYPE);
        String discountValue = scriptResult.get(DISCOUNT_VALUE);
        String discountStepSize = scriptResult.get(DISCOUNT_STEP_SIZE);
        String closeAt = scriptResult.get(CLOSE_AT);

        if (basePrice.isEmpty() || floorPrice.isEmpty() || discountType.isEmpty()
                || discountValue.isEmpty() || discountStepSize.isEmpty() || closeAt.isEmpty()) {
            throw new IllegalStateException(
                    "실시간 상태를 만들 Redis meta 값이 없습니다. groupPurchaseId=" + groupPurchaseId);
        }

        DiscountPolicy discountPolicy = new DiscountPolicy(
                Integer.parseInt(basePrice),
                Integer.parseInt(floorPrice),
                DiscountType.valueOf(discountType),
                Integer.parseInt(discountValue),
                Integer.parseInt(discountStepSize));

        return new GroupPurchaseLiveState(
                Long.parseLong(scriptResult.get(VERSION)),
                RedisValueParser.toStatus(emptyToNull(scriptResult.get(STATUS))),
                RedisValueParser.toInteger(emptyToNull(scriptResult.get(HEADCOUNT))),
                RedisValueParser.toInteger(emptyToNull(scriptResult.get(PRICE))),
                discountPolicy,
                RedisValueParser.toInteger(emptyToNull(scriptResult.get(MAX_HEADCOUNT))),
                LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(closeAt)), ClockConfig.SERVICE_ZONE));

    }

    public int headcountOrZero(){
        return headcount == null ? 0 : headcount;
    }

    public int priceOrBasePrice(){
        return price == null ? discountPolicy.basePrice() : price;
    }

    private static String emptyToNull(String value){
        return value.isEmpty() ? null : value;
    }

}
