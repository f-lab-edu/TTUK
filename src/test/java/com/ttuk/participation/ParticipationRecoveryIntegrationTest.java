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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ParticipationRecoveryIntegrationTest {

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
    PasswordEncoder passwordEncoder;
    @Autowired
    Clock clock;

    private Member seller;
    private Member buyer;
    private Cookie buyerCookie;

    @BeforeEach
    void setUp() throws Exception {
        seller = memberRepository.save(member("seller@ttuk.kr", "진도농장", MemberRole.SELLER, "01011112222"));
        buyer = memberRepository.save(member("buyer@ttuk.kr", "구매자", MemberRole.BUYER, "01033334444"));
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
    @DisplayName("Redis가 초기화되지 않은 진행 중 공동구매면 DB 값으로 초기화한 뒤 참여된다")
    void join_lazyInit() throws Exception {
        GroupPurchase groupPurchase = saveGroupPurchase(LocalDateTime.now(clock).plusDays(3));

        join(groupPurchase.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.currentHeadcount").value(1))
                .andExpect(jsonPath("$.data.currentPrice").value(3000));

        assertThat(redisTemplate.hasKey(GroupPurchaseRedisKeys.meta(groupPurchase.getId()))).isTrue();
        assertThat(participationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Redis가 없고 DB상 마감 시각이 지났으면 초기화하지 않고 409 GROUP_PURCHASE_NOT_OPEN이다")
    void join_lazyInit_afterClose() throws Exception {
        GroupPurchase groupPurchase = saveGroupPurchase(LocalDateTime.now(clock).minusDays(2));

        join(groupPurchase.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GROUP_PURCHASE_NOT_OPEN"));

        assertThat(redisTemplate.hasKey(GroupPurchaseRedisKeys.meta(groupPurchase.getId()))).isFalse();
    }

    @Test
    @DisplayName("Redis 참여 후 DB 저장이 실패하면 500이고 Redis 참여는 되돌려진다")
    void join_dbFailure_rollsBackRedis() throws Exception {
        GroupPurchase groupPurchase = saveGroupPurchase(LocalDateTime.now(clock).plusDays(3));
        Long id = groupPurchase.getId();
        groupPurchaseRedisRepository.initialize(GroupPurchaseCreatedEvent.from(groupPurchase));
        memberRepository.delete(buyer);

        join(id)
                .andExpect(status().isInternalServerError());

        assertThat(redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.headcount(id))).isEqualTo("0");
        assertThat(redisTemplate.opsForSet().isMember(GroupPurchaseRedisKeys.participants(id),
                String.valueOf(buyer.getId()))).isFalse();
        assertThat(participationRepository.count()).isZero();
    }

    private ResultActions join(Long groupPurchaseId) throws Exception {
        return mockMvc.perform(post("/api/group-purchases/{id}/participants", groupPurchaseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "quantity": 2 }
                        """)
                .cookie(buyerCookie));
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
                .discountStepSize(10)
                .maxHeadcount(50)
                .closeAt(closeAt.withNano(0))
                .build());
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