package com.ttuk.grouppurchase;

import com.ttuk.global.config.ClockConfig;
import com.ttuk.grouppurchase.event.GroupPurchaseCreatedEvent;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisKeys;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import com.ttuk.grouppurchase.redis.JoinOutcome;
import com.ttuk.grouppurchase.redis.JoinResult;
import com.ttuk.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Import(TestcontainersConfig.class)
class GroupPurchaseJoinRedisIntegrationTest {

    private static final long GROUP_PURCHASE_ID = 900L;

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
    @DisplayName("첫 참여는 인원 1·기본가를 돌려주고, 참여자 Set은 meta와 같은 시각에 만료된다")
    void join_first() {
        initialize(50, 10);

        JoinResult result = groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 7L);
        assertThat(result.outcome()).isEqualTo(JoinOutcome.JOINED);
        assertThat(result.headcount()).isEqualTo(1);
        assertThat(result.price()).isEqualTo(3000);
        assertThat(redisTemplate.opsForSet().isMember(participantsKey(), "7")).isTrue();
        assertThat(redisTemplate.getExpire(participantsKey()))
                .isCloseTo(redisTemplate.getExpire(GroupPurchaseRedisKeys.meta(GROUP_PURCHASE_ID)), within(1L));
    }

    @Test
    @DisplayName("참여할 때마다 계산한 단가가 DiscountPolicy와 같고, 정원 무제한이면 계속 참여된다")
    void join_priceMatchesDiscountPolicy() {
        initialize(null, 2);
        DiscountPolicy policy = new DiscountPolicy(3000, 2500, DiscountType.RATE, 5, 2);

        for (long memberId = 1; memberId <= 8; memberId++) {
            JoinResult result = groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, memberId);

            int expectedHeadcount = (int) memberId;
            assertThat(result.headcount()).isEqualTo(expectedHeadcount);
            assertThat(result.price()).isEqualTo(policy.priceAt(expectedHeadcount));
        }
        assertThat(redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.price(GROUP_PURCHASE_ID)))
                .isEqualTo("2500");
    }

    @Test
    @DisplayName("같은 회원이 다시 참여하면 ALREADY_JOINED이고 인원은 그대로다")
    void join_alreadyJoined() {
        initialize(50, 10);
        groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 7L);

        JoinResult result = groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 7L);

        assertThat(result.outcome()).isEqualTo(JoinOutcome.ALREADY_JOINED);
        assertThat(redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.headcount(GROUP_PURCHASE_ID)))
                .isEqualTo("1");
    }

    @Test
    @DisplayName("정원이 차면 상태가 CLOSED가 되고 만료 시각은 유지되며, 다음 참여는 FULL이다")
    void join_full() {
        initialize(2, 10);
        groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 1L);

        JoinResult last = groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 2L);
        JoinResult overflow = groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 3L);

        assertThat(last.outcome()).isEqualTo(JoinOutcome.JOINED);
        assertThat(last.headcount()).isEqualTo(2);
        assertThat(redisTemplate.opsForValue().get(statusKey())).isEqualTo("CLOSED");
        assertThat(redisTemplate.getExpire(statusKey()))
                .isCloseTo(secondsUntil(closeAt), within(5L));
        assertThat(redisTemplate.getExpire(GroupPurchaseRedisKeys.price(GROUP_PURCHASE_ID)))
                .isCloseTo(secondsUntil(closeAt.plusDays(1)), within(5L));
        assertThat(overflow.outcome()).isEqualTo(JoinOutcome.FULL);
    }

    @Test
    @DisplayName("Redis가 초기화되지 않았으면 NOT_INITIALIZED다")
    void join_notInitialized() {
        JoinResult result = groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 7L);

        assertThat(result.outcome()).isEqualTo(JoinOutcome.NOT_INITIALIZED);
    }

    @Test
    @DisplayName("마감 시각이 지나 상태 키가 없으면 NOT_OPEN이고 아무것도 바뀌지 않는다")
    void join_afterClose() {
        initialize(50, 10);
        redisTemplate.delete(statusKey());

        JoinResult result = groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 7L);

        assertThat(result.outcome()).isEqualTo(JoinOutcome.NOT_OPEN);
        assertThat(redisTemplate.hasKey(participantsKey())).isFalse();
        assertThat(redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.headcount(GROUP_PURCHASE_ID)))
                .isEqualTo("0");
    }

    private void initialize(Integer maxHeadcount, int discountStepSize) {
        closeAt = LocalDateTime.now(clock).plusDays(3).withNano(0);
        groupPurchaseRedisRepository.initialize(new GroupPurchaseCreatedEvent(
                GROUP_PURCHASE_ID, 1L, "대파 한 단",
                3000, 2500, DiscountType.RATE, 5, discountStepSize,
                maxHeadcount, closeAt));
    }

    private long secondsUntil(LocalDateTime dateTime) {
        return dateTime.atZone(ClockConfig.SERVICE_ZONE).toEpochSecond() - Instant.now(clock).getEpochSecond();
    }

    private String participantsKey() {
        return GroupPurchaseRedisKeys.participants(GROUP_PURCHASE_ID);
    }

    private String statusKey() {
        return GroupPurchaseRedisKeys.status(GROUP_PURCHASE_ID);
    }
}