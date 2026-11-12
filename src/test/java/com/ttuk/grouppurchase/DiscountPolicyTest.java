package com.ttuk.grouppurchase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountPolicyTest {

    // 기본가 3,000 · 하한가 2,500 · 10명마다 5% → 단계당 150원
    private final DiscountPolicy policy =
            new DiscountPolicy(3000, 2500, DiscountType.RATE, 5, 10);


    @ParameterizedTest(name = "{0}명 → {1}원")
    @CsvSource({
            "0,   3000",
            "9,   3000",
            "10,  2850",
            "19,  2850",
            "20,  2700",
            "40,  2500",
            "100, 2500"
    })
    @DisplayName("단계 인원에 도달할 때마다 할인되고, 하한가 밑으로는 내려가지 않는다")
    void priceAt(int headcount, int expectedPrice) {
        assertThat(policy.priceAt(headcount)).isEqualTo(expectedPrice);
    }

    @Test
    @DisplayName("다음 단계까지 남은 인원과 그때의 단가를 계산한다")
    void nextDiscount() {
        assertThat(policy.nextDiscount(17, null))
                .contains(new NextDiscount(3, 2700));
    }

    @Test
    @DisplayName("다음 단가가 하한가보다 낮게 계산되면 하한가로 보여 준다")
    void nextDiscount_clampedToFloor() {
        assertThat(policy.nextDiscount(30, null))
                .contains(new NextDiscount(10, 2500));
    }

    @Test
    @DisplayName("이미 하한가에 도달했으면 다음 할인이 없다")
    void nextDiscount_atFloor() {
        assertThat(policy.nextDiscount(40, null)).isEmpty();
    }

    @Test
    @DisplayName("다음 단계 인원이 정원을 넘으면 다음 할인이 없고, 정원과 같으면 있다")
    void nextDiscount_maxHeadcount() {
        assertThat(policy.nextDiscount(20, 25)).isEmpty();
        assertThat(policy.nextDiscount(20, 30))
                .contains(new NextDiscount(10, 2550));
    }

    @Test
    @DisplayName("단계당 할인이 0원이면 다음 할인이 없다")
    void nextDiscount_zeroDiscountPerStep() {
        DiscountPolicy tinyPrice = new DiscountPolicy(10, 1, DiscountType.RATE, 5, 2);

        assertThat(tinyPrice.nextDiscount(0, null)).isEmpty();
    }




}
