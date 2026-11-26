package com.ttuk.grouppurchase.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupPurchaseCancelResultTest {

    @Test
    @DisplayName("값을 함께 받는 결과인데 Lua가 값을 주지 않으면, 결과 종류와 개수를 담아 예외를 던진다")
    void from_missingValues() {
        assertThatThrownBy(() -> GroupPurchaseCancelResult.from(List.of(1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELED")
                .hasMessageContaining("actual=1");
    }

    @Test
    @DisplayName("값이 없는 결과인데 Lua가 값을 함께 주면, 조용히 버리지 않고 예외를 던진다")
    void from_unexpectedValues() {
        assertThatThrownBy(() -> GroupPurchaseCancelResult.from(List.of(-2L, 3L, 2850L, 3L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOT_OPEN")
                .hasMessageContaining("actual=4");
    }

    @Test
    @DisplayName("결과 종류와 개수가 맞으면 값을 읽는다")
    void from_canceled() {
        GroupPurchaseCancelResult result = GroupPurchaseCancelResult.from(List.of(1L, 3L, 2850L, 3L));

        assertThat(result.outcome()).isEqualTo(GroupPurchaseCancelOutcome.CANCELED);
        assertThat(result.headcount()).isEqualTo(3);
        assertThat(result.price()).isEqualTo(2850);
        assertThat(result.participantCount()).isEqualTo(3);
    }
}