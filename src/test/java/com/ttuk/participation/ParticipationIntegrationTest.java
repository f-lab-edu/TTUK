package com.ttuk.participation;

import com.ttuk.category.Category;
import com.ttuk.category.CategoryRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ParticipationIntegrationTest {

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
    @DisplayName("참여하면 201과 참여 후 인원·단가를 돌려주고, DB에 JOINED로 저장된다")
    void join() throws Exception {
        Long id = openGroupPurchase();

        join(id, buyerCookie)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.currentHeadcount").value(1))
                .andExpect(jsonPath("$.data.currentPrice").value(3000));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT group_purchase_id, member_id, quantity, status FROM participation");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("group_purchase_id", id)
                .containsEntry("member_id", buyer.getId())
                .containsEntry("quantity", 2)
                .containsEntry("status", "JOINED");
    }

    @Test
    @DisplayName("Lua가 참여를 거절하면 409이고 DB에 저장되지 않는다 (이미 참여)")
    void join_rejected() throws Exception {

        Long id = openGroupPurchase();
        join(id, buyerCookie);
        join(id, buyerCookie)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_JOINED"));
        assertThat(participationRepository.count()).isEqualTo(1);

    }

    @Test
    @DisplayName("Redis에도 DB에도 없는 공동구매면 404다")
    void join_notFound() throws Exception {
        join(999_999L, buyerCookie)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROUP_PURCHASE_NOT_FOUND"));
    }

    @Test
    @DisplayName("판매자는 참여할 수 없고 Redis 인원도 바뀌지 않는다 (403)")
    void join_seller() throws Exception {
        Long id = openGroupPurchase();

        join(id, sellerCookie)
                .andExpect(status().isForbidden());

        assertThat(redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.headcount(id))).isEqualTo("0");
    }

    private ResultActions join(Long groupPurchaseId, Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/group-purchases/{id}/participants", groupPurchaseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "quantity": 2 }
                        """)
                .cookie(cookie));
    }

    private Long openGroupPurchase() {
        Category category = categoryRepository.findById(FRESH_FOOD_ID).orElseThrow();
        LocalDateTime closeAt = LocalDateTime.now(clock).plusDays(3).withNano(0);
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
                .closeAt(closeAt)
                .build());

        groupPurchaseRedisRepository.initialize(new GroupPurchaseCreatedEvent(
                groupPurchase.getId(), FRESH_FOOD_ID, "대파 한 단",
                3000, 2500, DiscountType.RATE, 5, 10, 50, closeAt));
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