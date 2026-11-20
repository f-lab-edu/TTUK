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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class GroupPurchaseDetailNextDiscountIntegrationTest {

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
    private Cookie buyerCookie;
    private List<String> redisKeys = List.of();

    @BeforeEach
    void setUp() throws Exception {
        seller = memberRepository.save(member(SELLER_EMAIL, "진도농장", MemberRole.SELLER, "01011112222"));
        memberRepository.save(member(BUYER_EMAIL, "구매자", MemberRole.BUYER, "01033334444"));
        buyerCookie = loginAndGetCookie(BUYER_EMAIL);
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(redisKeys);
        groupPurchaseRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("진행 중이면 현재 인원 기준으로 다음 할인까지 남은 인원과 다음 단가가 내려간다")
    void detail_nextDiscount() throws Exception {
        Long id = saveGroupPurchase().getId();
        saveLiveValues(id, "17", "2850", "OPEN");

        mockMvc.perform(get("/api/group-purchases/{id}", id).cookie(buyerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentHeadcount").value(17))
                .andExpect(jsonPath("$.data.nextDiscountIn").value(3))
                .andExpect(jsonPath("$.data.nextPrice").value(2700));
    }

    @Test
    @DisplayName("진행 중이 아니면 다음 할인 정보는 null이다")
    void detail_notOpen_noNextDiscount() throws Exception {
        Long id = saveGroupPurchase().getId();
        saveLiveValues(id, "17", "2850", "CLOSED");

        mockMvc.perform(get("/api/group-purchases/{id}", id).cookie(buyerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.nextDiscountIn").value(nullValue()))
                .andExpect(jsonPath("$.data.nextPrice").value(nullValue()));
    }

    private void saveLiveValues(Long id, String headcount, String price, String status) {
        redisTemplate.opsForValue().set(GroupPurchaseRedisKeys.headcount(id), headcount);
        redisTemplate.opsForValue().set(GroupPurchaseRedisKeys.price(id), price);
        redisTemplate.opsForValue().set(GroupPurchaseRedisKeys.status(id), status);
        redisKeys = List.of(
                GroupPurchaseRedisKeys.headcount(id),
                GroupPurchaseRedisKeys.price(id),
                GroupPurchaseRedisKeys.status(id));
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