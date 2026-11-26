package com.ttuk.grouppurchase;

import com.ttuk.category.Category;
import com.ttuk.category.CategoryRepository;
import com.ttuk.grouppurchase.event.GroupPurchaseCreatedEvent;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisKeys;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import com.ttuk.member.Member;
import com.ttuk.member.MemberRepository;
import com.ttuk.member.MemberRole;
import com.ttuk.participation.Participation;
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
class GroupPurchaseSettlementIntegrationTest {

    private static final long FRESH_FOOD_ID = 1L;
    private static final int DISCOUNT_STEP_SIZE = 2;

    @Autowired
    GroupPurchaseSettlementService settlementService;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    GroupPurchaseRepository groupPurchaseRepository;
    @Autowired
    ParticipationRepository participationRepository;
    @Autowired
    ParticipationService participationService;
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
    @DisplayName("Redis status가 만료되면 Redis의 인원·단가로 CLOSED 처리하고, 참여는 JOINED 그대로 둔다")
    void settle() {
        Long id = openGroupPurchase();
        participationService.join(id, buyer1.getId(), 1);
        participationService.join(id, buyer2.getId(), 3);
        expireStatus(id);

        settlementService.settle(id);

        Map<String, Object> row = groupPurchaseRow(id);
        assertThat(row.get("status")).isEqualTo("CLOSED");
        assertThat(row.get("final_headcount")).isEqualTo(2);
        assertThat(row.get("final_price")).isEqualTo(2850);
        assertThat(row.get("closed_at")).isNotNull();
        assertThat(participationStatus(buyer1)).isEqualTo("JOINED");
        assertThat(participationStatus(buyer2)).isEqualTo("JOINED");
    }

    @Test
    @DisplayName("대조: DB에만 JOINED인 참여는 CANCELED로 정리하고, Redis에만 있는 참여자는 최종 인원에 포함한다")
    void settle_reconcile() {
        Long id = openGroupPurchase();
        participationService.join(id, buyer1.getId(), 1);
        groupPurchaseRedisRepository.rollbackJoin(id, buyer1.getId());
        groupPurchaseRedisRepository.join(id, buyer2.getId());
        expireStatus(id);

        settlementService.settle(id);

        Map<String, Object> row = groupPurchaseRow(id);
        assertThat(row.get("status")).isEqualTo("CLOSED");
        assertThat(row.get("final_headcount")).isEqualTo(1);
        assertThat(row.get("final_price")).isEqualTo(3000);
        assertThat(participationStatus(buyer1)).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("Redis status가 아직 OPEN이면 정산하지 않는다")
    void settle_stillOpen() {
        Long id = openGroupPurchase();
        participationService.join(id, buyer1.getId(), 1);

        settlementService.settle(id);

        Map<String, Object> row = groupPurchaseRow(id);
        assertThat(row.get("status")).isEqualTo("OPEN");
        assertThat(row.get("final_headcount")).isNull();
    }

    @Test
    @DisplayName("Redis 키가 없고 마감 시각이 지났으면, DB의 JOINED 수와 할인 규칙으로 마감한다")
    void settle_withoutRedis() {
        LocalDateTime now = LocalDateTime.now(clock);
        GroupPurchase groupPurchase = saveGroupPurchase(now.minusHours(1).withNano(0));
        participationRepository.save(participation(groupPurchase, buyer1, now.minusHours(2)));
        participationRepository.save(participation(groupPurchase, buyer2, now.minusHours(2)));

        settlementService.settle(groupPurchase.getId());

        Map<String, Object> row = groupPurchaseRow(groupPurchase.getId());
        assertThat(row.get("status")).isEqualTo("CLOSED");
        assertThat(row.get("final_headcount")).isEqualTo(2);
        assertThat(row.get("final_price")).isEqualTo(2850);
    }

    @Test
    @DisplayName("Redis status가 CANCELED(판매자 취소 진행 중)면 정산하지 않고 정산 표시도 남기지 않는다")
    void settle_canceledInRedis() {
        Long id = openGroupPurchase();
        participationService.join(id, buyer1.getId(), 1);
        redisTemplate.opsForValue().set(GroupPurchaseRedisKeys.status(id), "CANCELED");

        settlementService.settle(id);

        Map<String, Object> row = groupPurchaseRow(id);
        assertThat(row.get("status")).isEqualTo("OPEN");
        assertThat(row.get("final_headcount")).isNull();
        assertThat(redisTemplate.opsForHash().hasKey(GroupPurchaseRedisKeys.meta(id), "settled")).isFalse();
    }

    private void expireStatus(Long groupPurchaseId) {
        redisTemplate.delete(GroupPurchaseRedisKeys.status(groupPurchaseId));
    }

    private Map<String, Object> groupPurchaseRow(Long groupPurchaseId) {
        return jdbcTemplate.queryForMap(
                "SELECT status, final_headcount, final_price, closed_at FROM group_purchase WHERE id = ?",
                groupPurchaseId);
    }

    private String participationStatus(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM participation WHERE member_id = ?", String.class, member.getId());
    }

    private Long openGroupPurchase() {
        GroupPurchase groupPurchase = saveGroupPurchase(LocalDateTime.now(clock).plusDays(3).withNano(0));
        groupPurchaseRedisRepository.initialize(GroupPurchaseCreatedEvent.from(groupPurchase));
        return groupPurchase.getId();
    }

    private GroupPurchase saveGroupPurchase(LocalDateTime closeAt) {
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
                .discountStepSize(DISCOUNT_STEP_SIZE)
                .maxHeadcount(50)
                .closeAt(closeAt)
                .build());
    }

    private Participation participation(GroupPurchase groupPurchase, Member member, LocalDateTime joinedAt) {
        return Participation.builder()
                .groupPurchase(groupPurchase)
                .member(member)
                .quantity(1)
                .joinedAt(joinedAt)
                .build();
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