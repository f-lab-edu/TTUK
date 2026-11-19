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
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class GroupPurchaseCancelIntegrationTest {

    private static final String PASSWORD = "a123456789";
    private static final long FRESH_FOOD_ID = 1L;
    private static final String REASON = "산지 기상 악화로 물량 확보가 어렵습니다";

    @Autowired
    MockMvc mockMvc;
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
    PasswordEncoder passwordEncoder;
    @Autowired
    Clock clock;

    private Member seller;
    private Member buyer1;
    private Member buyer2;
    private Cookie sellerCookie;
    private Cookie otherSellerCookie;
    private Cookie buyerCookie;

    @BeforeEach
    void setUp() throws Exception {
        seller = memberRepository.save(member("seller@ttuk.kr", "진도농장", MemberRole.SELLER, "01011112222"));
        memberRepository.save(member("other@ttuk.kr", "해남농장", MemberRole.SELLER, "01077778888"));
        buyer1 = memberRepository.save(member("buyer1@ttuk.kr", "구매자1", MemberRole.BUYER, "01033334444"));
        buyer2 = memberRepository.save(member("buyer2@ttuk.kr", "구매자2", MemberRole.BUYER, "01055556666"));
        sellerCookie = loginAndGetCookie("seller@ttuk.kr");
        otherSellerCookie = loginAndGetCookie("other@ttuk.kr");
        buyerCookie = loginAndGetCookie("buyer1@ttuk.kr");
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
    @DisplayName("판매자가 취소하면 200이고, DB에 취소 정보·취소 시점 인원·단가가 저장되며 참여는 JOINED 그대로다")
    void cancel() throws Exception {

        Long id = openGroupPurchase(50);
        participationService.join(id, buyer1.getId(), 1);
        participationService.join(id, buyer2.getId(), 3);

        cancel(id, sellerCookie)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupPurchaseId").value(id))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.cancelReason").value(REASON))
                .andExpect(jsonPath("$.data.canceledAt").exists())
                .andExpect(jsonPath("$.data.notifiedCount").value(2));

        Map<String, Object> row = groupPurchaseRow(id);
        assertThat(row.get("status")).isEqualTo("CANCELED");
        assertThat(row.get("cancel_reason")).isEqualTo(REASON);
        assertThat(row.get("canceled_at")).isNotNull();
        assertThat(row.get("final_headcount")).isEqualTo(2);
        assertThat(row.get("final_price")).isEqualTo(2850);
        assertThat(row.get("closed_at")).isNull();
        assertThat(joinedCount(id)).isEqualTo(2);
        assertThat(redisStatus(id)).isEqualTo("CANCELED");

    }

    @Test
    @DisplayName("Redis에서만 취소된 상태에서 다시 요청하면 DB를 마무리하고 200이다")
    void cancel_finishPendingCancel() throws Exception {

        Long id = openGroupPurchase(50);
        participationService.join(id, buyer1.getId(), 1);
        groupPurchaseRedisRepository.cancelGroupPurchase(id);

        cancel(id, sellerCookie)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifiedCount").value(1));

        Map<String, Object> row = groupPurchaseRow(id);
        assertThat(row.get("status")).isEqualTo("CANCELED");
        assertThat(row.get("final_headcount")).isEqualTo(1);

    }

    @Test
    @DisplayName("정원이 차서 마감된 공동구매는 취소할 수 없다 (409)")
    void cancel_closed() throws Exception {

        Long id = openGroupPurchase(1);
        participationService.join(id, buyer1.getId(), 1);

        cancel(id, sellerCookie)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GROUP_PURCHASE_NOT_OPEN"));

    }

    @Test
    @DisplayName("다른 판매자의 공동구매는 취소할 수 없고 (403), Redis 상태도 바뀌지 않는다")
    void cancel_notOwner() throws Exception {

        Long id = openGroupPurchase(50);

        cancel(id, otherSellerCookie)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_PURCHASE_OWNER"));

        assertThat(redisStatus(id)).isEqualTo("OPEN");

    }

    @Test
    @DisplayName("구매자는 공동구매를 취소할 수 없다 (403)")
    void cancel_buyer() throws Exception {

        Long id = openGroupPurchase(50);

        cancel(id, buyerCookie)
                .andExpect(status().isForbidden());

    }

    private ResultActions cancel(Long groupPurchaseId, Cookie cookie) throws Exception {
        String json = """
                { "reason": "%s" }
                """.formatted(REASON);
        return mockMvc.perform(post("/api/group-purchases/{id}/cancel", groupPurchaseId)
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private Map<String, Object> groupPurchaseRow(Long groupPurchaseId) {
        return jdbcTemplate.queryForMap(
                "SELECT status, cancel_reason, canceled_at, final_headcount, final_price, closed_at "
                        + "FROM group_purchase WHERE id = ?", groupPurchaseId);
    }

    private Integer joinedCount(Long groupPurchaseId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM participation WHERE group_purchase_id = ? AND status = 'JOINED'",
                Integer.class, groupPurchaseId);
    }

    private String redisStatus(Long groupPurchaseId) {
        return redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.status(groupPurchaseId));
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
        groupPurchaseRedisRepository.initialize(groupPurchase);
        return groupPurchase.getId();
    }

    private Member member(String email, String nickname, MemberRole role, String phoneNum) {
        return Member.builder()
                .email(email)
                .encodedPassword(passwordEncoder.encode(PASSWORD))
                .nickname(nickname)
                .role(role)
                .phoneNum(phoneNum)
                .zipCode("06236")
                .address("서울시 강남구 테헤란로 123")
                .build();
    }

    private Cookie loginAndGetCookie(String email) throws Exception {
        String json = """
                { "email": "%s", "password": "%s" }
                """.formatted(email, PASSWORD);
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn().getResponse().getCookie("SESSION");
    }
}