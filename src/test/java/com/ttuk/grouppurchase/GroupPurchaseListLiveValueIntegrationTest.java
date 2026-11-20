package com.ttuk.grouppurchase;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class GroupPurchaseListLiveValueIntegrationTest {

    private static final String EMAIL = "seller@ttuk.kr";
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
    GroupPurchaseImageRepository groupPurchaseImageRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    Clock clock;

    private Member seller;
    private List<String> redisKeys = List.of();

    @BeforeEach
    void setUp() {
        seller = memberRepository.save(Member.builder()
                .email(EMAIL)
                .encodedPassword(passwordEncoder.encode(PASSWORD))
                .nickname("판매자")
                .role(MemberRole.SELLER)
                .phoneNum("01022223333")
                .zipCode("06236")
                .address("서울시 강남구 테헤란로 123")
                .build());
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(redisKeys);
        groupPurchaseImageRepository.deleteAll();
        groupPurchaseRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("Redis의 인원·단가가 목록에 반영되고, 키가 없는 공동구매는 인원 0·기본가로 나온다")
    void list_withLiveValues() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        GroupPurchase withKeys = save("대파 한 단", 3000, now.plusDays(1));
        save("양파 3kg", 8000, now.plusDays(2));

        String headcountKey = GroupPurchaseRedisKeys.headcount(withKeys.getId());
        String priceKey = GroupPurchaseRedisKeys.price(withKeys.getId());
        redisTemplate.opsForValue().set(headcountKey, "12");
        redisTemplate.opsForValue().set(priceKey, "2850");
        redisKeys = List.of(headcountKey, priceKey);

        mockMvc.perform(get("/api/group-purchases").cookie(loginAndGetCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("대파 한 단"))
                .andExpect(jsonPath("$.data.content[0].currentHeadcount").value(12))
                .andExpect(jsonPath("$.data.content[0].currentPrice").value(2850))
                .andExpect(jsonPath("$.data.content[1].title").value("양파 3kg"))
                .andExpect(jsonPath("$.data.content[1].currentHeadcount").value(0))
                .andExpect(jsonPath("$.data.content[1].currentPrice").value(8000));
    }

    private GroupPurchase save(String title, int basePrice, LocalDateTime closeAt) {
        GroupPurchase groupPurchase = groupPurchaseRepository.save(GroupPurchase.builder()
                .seller(seller)
                .category(categoryRepository.getReferenceById(FRESH_FOOD_ID))
                .title(title)
                .unit("개")
                .basePrice(basePrice)
                .floorPrice(basePrice / 2)
                .discountType(DiscountType.RATE)
                .discountValue(5)
                .discountStepSize(10)
                .closeAt(closeAt)
                .build());

        groupPurchaseImageRepository.save(GroupPurchaseImage.builder()
                .groupPurchase(groupPurchase)
                .imageKey("images/" + groupPurchase.getId() + ".png")
                .sortOrder(0)
                .build());
        return groupPurchase;
    }

    private Cookie loginAndGetCookie() throws Exception {
        String json = """
                { "email": "%s", "password": "%s" }
                """.formatted(EMAIL, PASSWORD);
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn().getResponse().getCookie("SESSION");
    }
}