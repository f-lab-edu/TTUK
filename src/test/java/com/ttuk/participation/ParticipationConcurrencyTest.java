package com.ttuk.participation;

import com.ttuk.category.Category;
import com.ttuk.category.CategoryRepository;
import com.ttuk.global.error.BusinessException;
import com.ttuk.grouppurchase.DiscountPolicy;
import com.ttuk.grouppurchase.DiscountType;
import com.ttuk.grouppurchase.GroupPurchase;
import com.ttuk.grouppurchase.GroupPurchaseRepository;
import com.ttuk.grouppurchase.event.GroupPurchaseCreatedEvent;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisKeys;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
class ParticipationConcurrencyTest {

    private static final int TIMEOUT_SECONDS = 30;
    private static final long FRESH_FOOD_ID = 1L;

    @Autowired
    ParticipationService participationService;
    @Autowired
    ParticipationRepository participationRepository;
    @Autowired
    GroupPurchaseRepository groupPurchaseRepository;
    @Autowired
    GroupPurchaseRedisRepository groupPurchaseRedisRepository;
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
        seller = memberRepository.save(member(0, MemberRole.SELLER));
    }

    @AfterEach
    void cleanUp() {
        Set<String> keys = redisTemplate.keys("group_purchase:*");
        redisTemplate.delete(keys);
        participationRepository.deleteAll();
        groupPurchaseRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("Redis가 초기화되지 않은 정원 50명 공동구매에 100명이 동시에 참여해도 정확히 50명만 성공한다")
    void join_concurrently_onlyMaxHeadcountSucceeds() throws Exception {

        GroupPurchase groupPurchase = saveGroupPurchase(50);
        Long id = groupPurchase.getId();
        List<Member> buyers = saveBuyers(100);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for(Member buyer : buyers){
            tasks.add(() -> tryJoin(id, buyer.getId()));
        }

        List<Boolean> results = runConcurrently(tasks);

        assertThat(results).filteredOn(joined -> joined).hasSize(50);
        assertThat(results).filteredOn(joined -> !joined).hasSize(50);
        assertThat(redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.headcount(id))).isEqualTo("50");
        assertThat(redisTemplate.opsForSet().size(GroupPurchaseRedisKeys.participants(id))).isEqualTo(50);
        assertThat(redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.status(id))).isEqualTo("CLOSED");
        assertThat(redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.price(id)))
                .isEqualTo(String.valueOf(policyOf(groupPurchase).priceAt(50)));
        assertThat(participationRepository.count()).isEqualTo(50);

    }

    @Test
    @DisplayName("한 회원이 같은 공동구매에 동시에 10번 참여해도 한 번만 성공한다")
    void join_sameMemberConcurrently_onlyOnceSucceeds() throws Exception {
        GroupPurchase groupPurchase = saveGroupPurchase(50);
        Long id = groupPurchase.getId();
        groupPurchaseRedisRepository.initialize(GroupPurchaseCreatedEvent.from(groupPurchase));
        Member buyer = saveBuyers(1).get(0);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> tryJoin(id, buyer.getId()));
        }
        List<Boolean> results = runConcurrently(tasks);

        assertThat(results).filteredOn(joined -> joined).hasSize(1);
        assertThat(redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.headcount(id))).isEqualTo("1");
        assertThat(participationRepository.count()).isEqualTo(1);
    }

    private GroupPurchase saveGroupPurchase(int maxHeadcount){
        Category category = categoryRepository.findById(FRESH_FOOD_ID).orElseThrow();
        return groupPurchaseRepository.save(GroupPurchase.builder()
                .seller(seller)
                .category(category)
                .title("대파 한 단")
                .description("진도 대파, 11/12 수확")
                .unit("단")
                .basePrice(3000)
                .floorPrice(2500)
                .discountType(DiscountType.RATE)
                .discountValue(5)
                .discountStepSize(10)
                .maxHeadcount(maxHeadcount)
                .closeAt(LocalDateTime.now(clock).plusDays(3).withNano(0))
                .build());
    }

    private boolean tryJoin(Long groupPurchaseId, Long memberId){
        try {
            participationService.join(groupPurchaseId, memberId, 1);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    private List<Boolean> runConcurrently(List<Callable<Boolean>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private DiscountPolicy policyOf(GroupPurchase groupPurchase){
        return new DiscountPolicy(groupPurchase.getBasePrice(), groupPurchase.getFloorPrice(),
                groupPurchase.getDiscountType(), groupPurchase.getDiscountValue(), groupPurchase.getDiscountStepSize());
    }

    private List<Member> saveBuyers(int count) {
        List<Member> buyers = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            buyers.add(member(i, MemberRole.BUYER));
        }
        return memberRepository.saveAll(buyers);

    }

    private Member member(int number, MemberRole role) {
        return Member.builder()
                .email("member" + number + "@ttuk.kr")
                .encodedPassword("not-used")
                .nickname("회원" + number)
                .role(role)
                .phoneNum("010%08d".formatted(number))
                .zipCode("06236")
                .address("서울시 강남구 테헤란로 123")
                .build();
    }


}