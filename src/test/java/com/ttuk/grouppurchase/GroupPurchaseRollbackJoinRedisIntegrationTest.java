package com.ttuk.grouppurchase;

import com.ttuk.global.config.ClockConfig;
import com.ttuk.grouppurchase.event.GroupPurchaseCreatedEvent;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Import(TestcontainersConfig.class)
class GroupPurchaseRollbackJoinRedisIntegrationTest {

    private static final long GROUP_PURCHASE_ID = 910L;

    @Autowired
    GroupPurchaseRedisRepository groupPurchaseRedisRepository;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    Clock clock;

    @AfterEach
    void cleanUp() {
        Set<String> keys = redisTemplate.keys("group_purchase:*");
        redisTemplate.delete(keys);
    }

    @Test
    @DisplayName("정원을 채운 참여를 되돌리면 인원·단가·참여자·상태가 직전으로 돌아가고, 다시 되돌려도 바뀌지 않는다")
    void rollbackJoin() {
        LocalDateTime closeAt = LocalDateTime.now(clock).plusDays(3).withNano(0);
        groupPurchaseRedisRepository.initialize(new GroupPurchaseCreatedEvent(
                GROUP_PURCHASE_ID, 1L, "대파 한 단",
                3000, 2500, DiscountType.RATE, 5, 2, 2, closeAt));
        groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 1L);
        groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 2L);

        boolean rolledBack = groupPurchaseRedisRepository.rollbackJoin(GROUP_PURCHASE_ID, 2L);
        boolean rolledBackAgain = groupPurchaseRedisRepository.rollbackJoin(GROUP_PURCHASE_ID, 2L);

        DiscountPolicy policy = new DiscountPolicy(3000, 2500, DiscountType.RATE, 5, 2);
        assertThat(rolledBack).isTrue();
        assertThat(rolledBackAgain).isFalse();
        assertThat(value(GroupPurchaseRedisKeys.headcount(GROUP_PURCHASE_ID))).isEqualTo("1");
        assertThat(value(GroupPurchaseRedisKeys.price(GROUP_PURCHASE_ID)))
                .isEqualTo(String.valueOf(policy.priceAt(1)));
        assertThat(redisTemplate.opsForSet().members(GroupPurchaseRedisKeys.participants(GROUP_PURCHASE_ID)))
                .containsExactly("1");
        assertThat(value(GroupPurchaseRedisKeys.status(GROUP_PURCHASE_ID))).isEqualTo("OPEN");
        assertThat(redisTemplate.getExpire(GroupPurchaseRedisKeys.status(GROUP_PURCHASE_ID)))
                .isCloseTo(closeAt.atZone(ClockConfig.SERVICE_ZONE).toEpochSecond()
                        - Instant.now(clock).getEpochSecond(), within(5L));
    }

    private String value(String key) {
        return redisTemplate.opsForValue().get(key);
    }
}