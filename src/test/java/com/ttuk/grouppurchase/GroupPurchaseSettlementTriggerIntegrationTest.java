package com.ttuk.grouppurchase;

import com.ttuk.category.Category;
import com.ttuk.category.CategoryRepository;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisKeys;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import com.ttuk.member.Member;
import com.ttuk.member.MemberRepository;
import com.ttuk.member.MemberRole;
import com.ttuk.participation.ParticipationRepository;
import com.ttuk.participation.ParticipationService;
import com.ttuk.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
class GroupPurchaseSettlementTriggerIntegrationTest {

    private static final long FRESH_FOOD_ID = 1L;

    @Autowired
    GroupPurchaseSettlementScheduler settlementScheduler;
    @Autowired
    ParticipationService participationService;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    GroupPurchaseRepository groupPurchaseRepository;
    @Autowired
    ParticipationRepository participationRepository;
    @Autowired
    GroupPurchaseRedisRepository groupPurchaseRedisRepository;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    Clock clock;

    private Member seller;
    private Member buyer1;
    private Member buyer2;

    @BeforeEach
    void setUp() {
        seller = memberRepository.save(member("seller@ttuk.kr", "진도농장", MemberRole.SELLER, "01011112222"));
        buyer1 = memberRepository.save(member("buyer1@ttuk.kr", "구매자1", MemberRole.BUYER, "01033334444"));
        buyer2 = memberRepository.save(member("buyer2@ttuk.kr", "구매자2", MemberRole.BUYER, "01055556666"));
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
    @DisplayName("스케줄러는 마감 후 1분이 지난 OPEN 공동구매만 정산한다")
    void scheduler_settlesOnlyClosedOverOneMinute() {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        GroupPurchase closedLongAgo = saveGroupPurchase(now.minusMinutes(2), 50);
        GroupPurchase closedJustNow = saveGroupPurchase(now.minusSeconds(30), 50);
        GroupPurchase stillOpen = saveGroupPurchase(now.plusDays(3), 50);

        settlementScheduler.settleClosedGroupPurchases();

        assertThat(statusOf(closedLongAgo.getId())).isEqualTo("CLOSED");
        assertThat(statusOf(closedJustNow.getId())).isEqualTo("OPEN");
        assertThat(statusOf(stillOpen.getId())).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("스케줄러는 한 공동구매의 정산이 실패해도 다음 공동구매를 정산한다")
    void scheduler_continuesAfterFailure() {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        GroupPurchase broken = saveGroupPurchase(now.plusDays(3), 50);
        groupPurchaseRedisRepository.initialize(broken);
        jdbcTemplate.update("UPDATE group_purchase SET close_at = ? WHERE id = ?",
                now.minusHours(2), broken.getId());
        redisTemplate.delete(GroupPurchaseRedisKeys.status(broken.getId()));
        redisTemplate.delete(GroupPurchaseRedisKeys.headcount(broken.getId()));
        GroupPurchase normal = saveGroupPurchase(now.minusHours(1), 50);

        settlementScheduler.settleClosedGroupPurchases();

        assertThat(statusOf(broken.getId())).isEqualTo("OPEN");
        assertThat(statusOf(normal.getId())).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("정원을 채운 참여 직후 바로 정산하고, 그 전 참여에서는 정산하지 않는다")
    void join_settlesWhenFilled() {
        GroupPurchase groupPurchase = saveGroupPurchase(LocalDateTime.now(clock).plusDays(3).withNano(0), 2);
        groupPurchaseRedisRepository.initialize(groupPurchase);
        Long id = groupPurchase.getId();

        participationService.join(id, buyer1.getId(), 1);
        assertThat(statusOf(id)).isEqualTo("OPEN");

        participationService.join(id, buyer2.getId(), 1);
        Map<String, Object> row = groupPurchaseRow(id);
        assertThat(row.get("status")).isEqualTo("CLOSED");
        assertThat(row.get("final_headcount")).isEqualTo(2);
        assertThat(row.get("final_price")).isEqualTo(2850);
    }

    private String statusOf(Long groupPurchaseId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM group_purchase WHERE id = ?", String.class, groupPurchaseId);
    }

    private Map<String, Object> groupPurchaseRow(Long groupPurchaseId) {
        return jdbcTemplate.queryForMap(
                "SELECT status, final_headcount, final_price FROM group_purchase WHERE id = ?",
                groupPurchaseId);
    }

    private GroupPurchase saveGroupPurchase(LocalDateTime closeAt, int maxHeadcount) {
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
                .discountStepSize(2)
                .maxHeadcount(maxHeadcount)
                .closeAt(closeAt)
                .build());
    }

    private Member member(String email, String nickname, MemberRole role, String phoneNum) {
        return Member.builder()
                .email(email)
                .encodedPassword("{noop}a123456789")
                .nickname(nickname)
                .role(role)
                .phoneNum(phoneNum)
                .zipCode("06236")
                .address("서울시 강남구 테헤란로 123")
                .build();
    }
}