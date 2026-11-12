package com.ttuk.grouppurchase;

import com.ttuk.category.Category;
import com.ttuk.category.CategoryRepository;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisKeys;
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class GroupPurchaseDetailLiveIntegrationTest {

    private static final String SELLER_EMAIL = "seller@ttuk.kr";
    private static final String BUYER_EMAIL = "buyer@ttuk.kr";
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
    StringRedisTemplate redisTemplate;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    Clock clock;

    private Member seller;
    private Member buyer;
    private Cookie buyerCookie;
    private List<String> redisKeys = List.of();

    @BeforeEach
    void setUp() throws Exception {
        seller = memberRepository.save(member(SELLER_EMAIL, "진도농장", MemberRole.SELLER, "01011112222"));
        buyer = memberRepository.save(member(BUYER_EMAIL, "구매자", MemberRole.BUYER, "01033334444"));
        buyerCookie = loginAndGetCookie(BUYER_EMAIL);
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(redisKeys);
        groupPurchaseRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("Redis 값이 있으면 현재 인원·단가·상태를 Redis에서 읽고, 참여한 회원은 joined가 true다")
    void detail_withLiveValues() throws Exception {
        GroupPurchase groupPurchase = saveGroupPurchase();
        Long id = groupPurchase.getId();

        redisTemplate.opsForValue().set(GroupPurchaseRedisKeys.headcount(id), "12");
        redisTemplate.opsForValue().set(GroupPurchaseRedisKeys.price(id), "2850");
        redisTemplate.opsForValue().set(GroupPurchaseRedisKeys.status(id), "CLOSED");
        redisTemplate.opsForSet().add(GroupPurchaseRedisKeys.participants(id), String.valueOf(buyer.getId()));
        redisKeys = List.of(
                GroupPurchaseRedisKeys.headcount(id),
                GroupPurchaseRedisKeys.price(id),
                GroupPurchaseRedisKeys.status(id),
                GroupPurchaseRedisKeys.participants(id));

        mockMvc.perform(get("/api/group-purchases/{id}", id).cookie(buyerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentHeadcount").value(12))
                .andExpect(jsonPath("$.data.currentPrice").value(2850))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.joined").value(true));
    }

    @Test
    @DisplayName("Redis 키가 없으면 0명·기본가·DB 상태로 내려가고 joined는 false다")
    void detail_withoutLiveValues() throws Exception {
        GroupPurchase groupPurchase = saveGroupPurchase();

        mockMvc.perform(get("/api/group-purchases/{id}", groupPurchase.getId()).cookie(buyerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentHeadcount").value(0))
                .andExpect(jsonPath("$.data.currentPrice").value(3000))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.joined").value(false));
    }

    private GroupPurchase saveGroupPurchase() {
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
                .closeAt(LocalDateTime.now(clock).plusDays(3).withNano(0))
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