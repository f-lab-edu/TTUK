package com.ttuk.grouppurchase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountTypeTest {

    @Test
    @DisplayName("RATE는 1~50만 허용한다")
    void rate_validRange() {
        assertThat(DiscountType.RATE.isValidValue(0, 3000)).isFalse();
        assertThat(DiscountType.RATE.isValidValue(1, 3000)).isTrue();
        assertThat(DiscountType.RATE.isValidValue(50, 3000)).isTrue();
        assertThat(DiscountType.RATE.isValidValue(51, 3000)).isFalse();
    }

    @Test
    @DisplayName("AMOUNT는 1원 이상, 기본가 미만만 허용한다")
    void amount_validRange() {
        assertThat(DiscountType.AMOUNT.isValidValue(0, 3000)).isFalse();
        assertThat(DiscountType.AMOUNT.isValidValue(1, 3000)).isTrue();
        assertThat(DiscountType.AMOUNT.isValidValue(2999, 3000)).isTrue();
        assertThat(DiscountType.AMOUNT.isValidValue(3000, 3000)).isFalse();
    }

    @Test
    @DisplayName("RATE의 구간당 할인액: 기본가 × 할인율 ÷ 100, 원 미만은 버린다")
    void rate_discountPerStep() {
        assertThat(DiscountType.RATE.discountPerStep(3000, 5)).isEqualTo(150);
        assertThat(DiscountType.RATE.discountPerStep(2990, 5)).isEqualTo(149);
    }
}