package com.ttuk.grouppurchase.redis;

import com.ttuk.grouppurchase.DiscountPolicy;
import com.ttuk.grouppurchase.DiscountType;
import com.ttuk.grouppurchase.GroupPurchaseStatus;
import com.ttuk.grouppurchase.event.GroupPurchaseCreatedEvent;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Import(TestcontainersConfig.class)
class GroupPurchaseBroadcastStateRedisIntegrationTest {

    private static final long GROUP_PURCHASE_ID = 930L;

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
    @DisplayName("방송 상태를 읽으면 인원·단가·상태·할인 규칙·정원·마감 시각을 돌려주고, 첫 version은 Redis 서버 시각(밀리초)에서 시작한다")
    void takeBroadcastState() {

        LocalDateTime closeAt = closeAtInThreeDays();
        initialize(50, closeAt);
        groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 1L);
        groupPurchaseRedisRepository.join(GROUP_PURCHASE_ID, 2L);

        GroupPurchaseLiveState state = take();

        assertThat(state.version()).isCloseTo(Instant.now(clock).toEpochMilli(), within(60_000L));
        assertThat(state.status()).isEqualTo(GroupPurchaseStatus.OPEN);
        assertThat(state.headcount()).isEqualTo(2);
        assertThat(state.price()).isEqualTo(3000);
        assertThat(state.discountPolicy()).isEqualTo(new DiscountPolicy(3000, 2500, DiscountType.RATE, 5, 10));
        assertThat(state.maxHeadcount()).isEqualTo(50);
        assertThat(state.closeAt()).isEqualTo(closeAt);

    }

    @Test
    @DisplayName("방송할 때마다 version이 1씩 늘어난다")
    void takeBroadcastState_incrementsVersion() {

        initialize(50, closeAtInThreeDays());

        long first = take().version();
        long second = take().version();

        assertThat(second).isEqualTo(first + 1);

    }

    @Test
    @DisplayName("version 키가 사라져도(Redis가 비워진 뒤) 새 version은 이전에 보낸 version보다 크다")
    void takeBroadcastState_versionKeyLost() {

        initialize(50, closeAtInThreeDays());
        long tenMinutesAgo = Instant.now(clock).minus(Duration.ofMinutes(10)).toEpochMilli();
        redisTemplate.opsForValue().set(GroupPurchaseRedisKeys.version(GROUP_PURCHASE_ID), String.valueOf(tenMinutesAgo));
        long beforeLost = take().version();
        redisTemplate.delete(GroupPurchaseRedisKeys.version(GROUP_PURCHASE_ID));

        long afterLost = take().version();

        assertThat(beforeLost).isEqualTo(tenMinutesAgo + 1);
        assertThat(afterLost).isGreaterThan(beforeLost);

    }

    @Test
    @DisplayName("version 키는 meta와 같은 시각에 만료된다")
    void takeBroadcastState_versionExpiresWithMeta() {
        initialize(50, closeAtInThreeDays());

        take();

        Long metaTtl = redisTemplate.getExpire(GroupPurchaseRedisKeys.meta(GROUP_PURCHASE_ID));
        Long versionTtl = redisTemplate.getExpire(GroupPurchaseRedisKeys.version(GROUP_PURCHASE_ID));
        assertThat(versionTtl).isCloseTo(metaTtl, within(2L));
    }

    @Test
    @DisplayName("정원이 무제한이면 maxHeadcount가, 상태 키가 마감 시각에 만료됐으면 status가 비어 있다")
    void takeBroadcastState_unlimitedAndStatusExpired() {
        initialize(null, closeAtInThreeDays());
        redisTemplate.delete(GroupPurchaseRedisKeys.status(GROUP_PURCHASE_ID));

        GroupPurchaseLiveState state = take();

        assertThat(state.maxHeadcount()).isNull();
        assertThat(state.status()).isNull();
    }

    @Test
    @DisplayName("Redis에 공동구매 키가 없으면 빈 결과이고 version 키도 만들지 않는다")
    void takeBroadcastState_noMeta() {
        Optional<GroupPurchaseLiveState> state = groupPurchaseRedisRepository.takeBroadcastState(GROUP_PURCHASE_ID);

        assertThat(state).isEmpty();
        assertThat(redisTemplate.hasKey(GroupPurchaseRedisKeys.version(GROUP_PURCHASE_ID))).isFalse();
    }

    private GroupPurchaseLiveState take() {
        return groupPurchaseRedisRepository.takeBroadcastState(GROUP_PURCHASE_ID).orElseThrow();
    }

    private LocalDateTime closeAtInThreeDays() {
        return LocalDateTime.now(clock).plusDays(3).withNano(0);
    }

    private void initialize(Integer maxHeadcount, LocalDateTime closeAt) {
        groupPurchaseRedisRepository.initialize(new GroupPurchaseCreatedEvent(
                GROUP_PURCHASE_ID, 1L, "대파 한 단",
                3000, 2500, DiscountType.RATE, 5, 10, maxHeadcount, closeAt));
    }

}