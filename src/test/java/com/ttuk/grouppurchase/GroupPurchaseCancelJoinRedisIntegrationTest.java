package com.ttuk.grouppurchase;

import com.ttuk.global.config.ClockConfig;
import com.ttuk.grouppurchase.event.GroupPurchaseCreatedEvent;
import com.ttuk.grouppurchase.redis.CancelOutcome;
import com.ttuk.grouppurchase.redis.CancelResult;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisKeys;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import com.ttuk.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Import(TestcontainersConfig.class)
class GroupPurchaseCancelJoinRedisIntegrationTest {

    private static final long GROUP_PURCHASE_ID = 920L;
    private static final Duration CANCELABLE_BEFORE = Duration.ofHours(1);

    @Autowired
    GroupPurchaseRedisRepository groupPurchaseRedisRepository;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    Clock clock;

    private LocalDateTime closeAt;

    @AfterEach
    void cleanUp() {
        Set<String> keys = redisTemplate.keys("group_purchase:*");
        redisTemplate.delete(keys);
    }


    @Test
    @DisplayName("취소하면 인원·참여자·단가가 줄어든 인원 기준으로 바뀌고, 같은 회원이 다시 취소하면 NOT_JOINED이며 아무것도 바뀌지 않는다")
    void cancel() {

        initialize(50);

        groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 1L);
        groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 2L);
        LocalDateTime now = LocalDateTime.now(clock);

        CancelResult result = groupPurchaseRedisRepository.cancel(GROUP_PURCHASE_ID, 2L, now, CANCELABLE_BEFORE);
        CancelResult again = groupPurchaseRedisRepository.cancel(GROUP_PURCHASE_ID, 2L, now, CANCELABLE_BEFORE);

        DiscountPolicy policy = new DiscountPolicy(3000, 2500, DiscountType.RATE, 5, 2);

        assertThat(result.outcome()).isEqualTo(CancelOutcome.CANCELED);
        assertThat(result.headcount()).isEqualTo(1);
        assertThat(result.price()).isEqualTo(policy.priceAt(1));
        assertThat(again.outcome()).isEqualTo(CancelOutcome.NOT_JOINED);
        assertThat(value(GroupPurchaseRedisKeys.headcount(GROUP_PURCHASE_ID))).isEqualTo("1");
        assertThat(redisTemplate.opsForSet().members(GroupPurchaseRedisKeys.participants(GROUP_PURCHASE_ID)))
                .containsExactly("1");
        assertThat(value(GroupPurchaseRedisKeys.status(GROUP_PURCHASE_ID))).isEqualTo("OPEN");
        assertThat(redisTemplate.getExpire(GroupPurchaseRedisKeys.price(GROUP_PURCHASE_ID)))
                .isCloseTo(secondsUntil(closeAt.plusDays(1)), within(5L));

    }

    @Test
    @DisplayName("마감 1시간 전 정각부터는 DEADLINE_PASSED이고, 그 1초 전까지는 취소된다")
    void cancel_deadline() {
        initialize(50);
        groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 1L);
        LocalDateTime deadline = closeAt.minus(CANCELABLE_BEFORE);

        CancelResult atDeadline = groupPurchaseRedisRepository.cancel(GROUP_PURCHASE_ID, 1L, deadline, CANCELABLE_BEFORE);
        String headcountAfterRejected = value(GroupPurchaseRedisKeys.headcount(GROUP_PURCHASE_ID));
        CancelResult justBefore = groupPurchaseRedisRepository.cancel(
                GROUP_PURCHASE_ID, 1L, deadline.minusSeconds(1), CANCELABLE_BEFORE);

        assertThat(atDeadline.outcome()).isEqualTo(CancelOutcome.DEADLINE_PASSED);
        assertThat(headcountAfterRejected).isEqualTo("1");
        assertThat(justBefore.outcome()).isEqualTo(CancelOutcome.CANCELED);
    }

    @Test
    @DisplayName("정원이 차서 CLOSED가 된 공동구매는 취소할 수 없고 인원은 그대로다")
    void cancel_afterFull() {
        initialize(2);
        groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 1L);
        groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 2L);

        CancelResult result = groupPurchaseRedisRepository.cancel(
                GROUP_PURCHASE_ID, 1L, LocalDateTime.now(clock), CANCELABLE_BEFORE);

        assertThat(result.outcome()).isEqualTo(CancelOutcome.NOT_OPEN);
        assertThat(value(GroupPurchaseRedisKeys.headcount(GROUP_PURCHASE_ID))).isEqualTo("2");
    }


    private void initialize(Integer maxHeadcount) {
        closeAt = LocalDateTime.now(clock).plusDays(3).withNano(0);
        groupPurchaseRedisRepository.initialize(new GroupPurchaseCreatedEvent(
                GROUP_PURCHASE_ID, 1L, "대파 한 단",
                3000, 2500, DiscountType.RATE, 5, 2, maxHeadcount, closeAt));
    }

    private long secondsUntil(LocalDateTime dateTime) {
        return dateTime.atZone(ClockConfig.SERVICE_ZONE).toEpochSecond() - Instant.now(clock).getEpochSecond();
    }

    private String value(String key) {
        return redisTemplate.opsForValue().get(key);
    }

}
