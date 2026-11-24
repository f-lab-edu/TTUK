package com.ttuk.participation;

import com.ttuk.category.Category;
import com.ttuk.category.CategoryRepository;
import com.ttuk.grouppurchase.DiscountType;
import com.ttuk.grouppurchase.GroupPurchase;
import com.ttuk.grouppurchase.GroupPurchaseRepository;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisKeys;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import com.ttuk.member.Member;
import com.ttuk.member.MemberRepository;
import com.ttuk.member.MemberRole;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ParticipationCancelIntegrationTest {

    private static final String PASSWORD = "a123456789";
    private static final long FRESH_FOOD_ID = 1L;

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
    private Member buyer;
    private Cookie sellerCookie;
    private Cookie buyerCookie;

    @BeforeEach
    void setUp() throws Exception {
        seller = memberRepository.save(member("seller@ttuk.kr", "진도농장", MemberRole.SELLER, "01011112222"));
        buyer = memberRepository.save(member("buyer@ttuk.kr", "구매자", MemberRole.BUYER, "01033334444"));
        sellerCookie = loginAndGetCookie("seller@ttuk.kr");
        buyerCookie = loginAndGetCookie("buyer@ttuk.kr");
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
    @DisplayName("참여를 취소하면 204이고, DB 참여는 CANCELED·취소 시각·취소 주체 BUYER가 기록되며 Redis 인원이 줄어든다")
    void cancel() throws Exception {
        Long id = openGroupPurchase();
        participationService.join(id, buyer.getId(), 2);

        cancel(id, buyerCookie).andExpect(status().isNoContent());
        Map<String, Object> row = participationRow();
        assertThat(row.get("status")).isEqualTo("CANCELED");
        assertThat(row.get("canceled_at")).isNotNull();
        assertThat(row.get("canceled_by")).isEqualTo("BUYER");
        assertThat(redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.headcount(id))).isEqualTo("0");

    }

    @Test
    @DisplayName("Redis에서는 이미 취소됐는데 DB가 JOINED로 남아 있으면, 다시 요청할 때 DB를 마무리하고 204다 (취소 주체 BUYER)")
    void cancel_finishPendingCancel() throws Exception {

        Long id = openGroupPurchase();
        participationService.join(id, buyer.getId(), 2);
        groupPurchaseRedisRepository.cancel(id, buyer.getId(), LocalDateTime.now(clock), GroupPurchase.CANCELABLE_BEFORE_CLOSE);
        cancel(id, buyerCookie).andExpect(status().isNoContent());
        Map<String, Object> row = participationRow();
        assertThat(row.get("status")).isEqualTo("CANCELED");
        assertThat(row.get("canceled_by")).isEqualTo("BUYER");

    }

    @Test
    @DisplayName("참여하지 않은 공동구매를 취소하면 404 NOT_JOINED다")
    void cancel_notJoined() throws Exception {
        Long id = openGroupPurchase();

        cancel(id, buyerCookie)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_JOINED"));
    }

    @Test
    @DisplayName("판매자는 참여 취소를 할 수 없다 (403)")
    void cancel_seller() throws Exception {
        Long id = openGroupPurchase();

        cancel(id, sellerCookie)
                .andExpect(status().isForbidden());
    }

    private ResultActions cancel(Long groupPurchaseId, Cookie cookie) throws Exception {
        return mockMvc.perform(delete("/api/group-purchases/{id}/participants/me", groupPurchaseId)
                .cookie(cookie));
    }

    private Map<String, Object> participationRow() {
        return jdbcTemplate.queryForMap("SELECT status, canceled_at, canceled_by FROM participation");
    }

    private Long openGroupPurchase() {
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
                .discountStepSize(10)
                .maxHeadcount(50)
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
