package com.ttuk.grouppurchase.redis;

import com.ttuk.category.Category;
import com.ttuk.category.CategoryRepository;
import com.ttuk.grouppurchase.DiscountType;
import com.ttuk.grouppurchase.GroupPurchase;
import com.ttuk.grouppurchase.GroupPurchaseRepository;
import com.ttuk.grouppurchase.event.GroupPurchaseCreatedEvent;
import com.ttuk.member.Member;
import com.ttuk.member.MemberRepository;
import com.ttuk.member.MemberRole;
import com.ttuk.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Import(TestcontainersConfig.class)
class GroupPurchaseCancelRedisIntegrationTest {

    private static final long FRESH_FOOD_ID = 1L;

    @Autowired
    GroupPurchaseRedisRepository groupPurchaseRedisRepository;
    @Autowired
    GroupPurchaseRepository groupPurchaseRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    Clock clock;

    private Member seller;

    @BeforeEach
    void setUp() {
        seller = memberRepository.save(Member.builder()
                .email("seller@ttuk.kr")
                .encodedPassword("{noop}a123456789")
                .nickname("진도농장")
                .role(MemberRole.SELLER)
                .phoneNum("01011112222")
                .zipCode("06236")
                .address("서울시 강남구 테헤란로 123")
                .build());
    }

    @AfterEach
    void cleanUp() {
        Set<String> keys = redisTemplate.keys("group_purchase:*");
        redisTemplate.delete(keys);
        groupPurchaseRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("OPEN이면 CANCELED로 바꾸고 만료 시각은 유지하며, 취소 시점 인원·단가·참여자 수를 돌려준다")
    void cancel() {
        Long id = openGroupPurchase(50);
        groupPurchaseRedisRepository.join(id, 101L);
        groupPurchaseRedisRepository.join(id, 102L);
        long ttlBefore = statusTtl(id);

        GroupPurchaseCancelResult result = groupPurchaseRedisRepository.cancelGroupPurchase(id);

        assertThat(result.outcome()).isEqualTo(GroupPurchaseCancelOutcome.CANCELED);
        assertThat(result.headcount()).isEqualTo(2);
        assertThat(result.price()).isEqualTo(2850);
        assertThat(result.participantCount()).isEqualTo(2);
        assertThat(status(id)).isEqualTo("CANCELED");
        assertThat(statusTtl(id)).isCloseTo(ttlBefore, within(1L));
        assertThat(groupPurchaseRedisRepository.join(id, 103L).outcome()).isEqualTo(JoinOutcome.NOT_OPEN);
    }

    @Test
    @DisplayName("이미 CANCELED면 바꾸지 않고 같은 인원·단가·참여자 수를 돌려준다")
    void cancel_alreadyCanceled() {
        Long id = openGroupPurchase(50);
        groupPurchaseRedisRepository.join(id, 101L);
        groupPurchaseRedisRepository.cancelGroupPurchase(id);

        GroupPurchaseCancelResult result = groupPurchaseRedisRepository.cancelGroupPurchase(id);

        assertThat(result.outcome()).isEqualTo(GroupPurchaseCancelOutcome.ALREADY_CANCELED);
        assertThat(result.headcount()).isEqualTo(1);
        assertThat(result.price()).isEqualTo(3000);
        assertThat(result.participantCount()).isEqualTo(1);
        assertThat(status(id)).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("정원이 차서 CLOSED면 거절하고 status를 바꾸지 않는다")
    void cancel_closed() {
        Long id = openGroupPurchase(1);
        groupPurchaseRedisRepository.join(id, 101L);

        GroupPurchaseCancelResult result = groupPurchaseRedisRepository.cancelGroupPurchase(id);

        assertThat(result.outcome()).isEqualTo(GroupPurchaseCancelOutcome.NOT_OPEN);
        assertThat(result.headcount()).isNull();
        assertThat(status(id)).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("되돌리기: CANCELED를 OPEN으로 되돌리고 만료 시각은 유지하며, 다시 참여할 수 있다")
    void reopen() {
        Long id = openGroupPurchase(50);
        long ttlBefore = statusTtl(id);
        groupPurchaseRedisRepository.cancelGroupPurchase(id);

        boolean reopened = groupPurchaseRedisRepository.reopenGroupPurchase(id);

        assertThat(reopened).isTrue();
        assertThat(status(id)).isEqualTo("OPEN");
        assertThat(statusTtl(id)).isCloseTo(ttlBefore, within(1L));
        assertThat(groupPurchaseRedisRepository.join(id, 101L).outcome()).isEqualTo(JoinOutcome.JOINED);
    }

    @Test
    @DisplayName("되돌리기: status가 만료돼 없으면 아무것도 하지 않고 키를 새로 만들지 않는다")
    void reopen_expired() {
        Long id = openGroupPurchase(50);
        groupPurchaseRedisRepository.cancelGroupPurchase(id);
        redisTemplate.delete(GroupPurchaseRedisKeys.status(id));

        boolean reopened = groupPurchaseRedisRepository.reopenGroupPurchase(id);

        assertThat(reopened).isFalse();
        assertThat(redisTemplate.hasKey(GroupPurchaseRedisKeys.status(id))).isFalse();
    }

    private String status(Long groupPurchaseId) {
        return redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.status(groupPurchaseId));
    }

    private long statusTtl(Long groupPurchaseId) {
        return redisTemplate.getExpire(GroupPurchaseRedisKeys.status(groupPurchaseId), TimeUnit.SECONDS);
    }

    private Long openGroupPurchase(int maxHeadcount) {
        Category category = categoryRepository.findById(FRESH_FOOD_ID).orElseThrow();
        GroupPurchase groupPurchase = groupPurchaseRepository.save(GroupPurchase.builder()
                .seller(seller)
                .category(category)
                .title("대파 한 단")
                .description("진도 대파, 11/12 수확")
                .unit("단")
                .basePrice(3000)
                .floorPrice(2500)
                .discountType(DiscountType.RATE)
                .discountValue(5)
                .discountStepSize(2)
                .maxHeadcount(maxHeadcount)
                .closeAt(LocalDateTime.now(clock).plusDays(3).withNano(0))
                .build());
        groupPurchaseRedisRepository.initialize(GroupPurchaseCreatedEvent.from(groupPurchase));
        return groupPurchase.getId();
    }
}