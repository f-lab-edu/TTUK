package com.ttuk.grouppurchase;

import java.util.Optional;


/*
* priceAt(17) -> 17/10 = 1 → 3000 − 1×150 = 2850
* nextDiscount(17, 50)
* nextStep          = 1 + 1 = 2
  nextStepHeadcount = 2 × 10 = 20        (20 ≤ 50 → 통과)
  nextPrice         = 3000 − 2×150 = 2700
  2700 < 2850       → 내려감 → 통과
  → NextDiscount(20 − 17 = 3, 2700)
* */

public record DiscountPolicy(
        int basePrice,
        int floorPrice,
        DiscountType discountType,
        int discountValue,
        int discountStepSize
) {

    public int priceAt(int headcount) {
        return priceAtStep(headcount / discountStepSize);
    }

    public Optional<NextDiscount> nextDiscount(int headcount, Integer maxHeadcount) {
        int currentStep = headcount / discountStepSize;
        int nextStep = currentStep + 1;
        int nextDiscountHeadcount = nextStep * discountStepSize;

        if (maxHeadcount != null && nextDiscountHeadcount > maxHeadcount) {
            return Optional.empty();
        }

        int currentPrice = priceAtStep(currentStep);
        int nextPrice = priceAtStep(nextStep);
        if (nextPrice >= currentPrice) {
            return Optional.empty();
        }

        int remainingHeadcount = nextDiscountHeadcount - headcount;
        return Optional.of(new NextDiscount(remainingHeadcount, nextPrice));
    }

    private int priceAtStep(int step) {
        long discounted = basePrice - (long) step * discountType.discountPerStep(basePrice, discountValue);
        return (int) Math.max(floorPrice, discounted);
    }
}
