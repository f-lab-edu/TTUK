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
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public class GroupPurchaseListIntegrationTest {


    private static final String EMAIL = "seller@ttuk.kr";
    private static final String PASSWORD = "a123456789";
    private static final long FRESH_FOOD_ID = 1L;
    private static final long PROCESSED_FOOD_ID = 2L;

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
    private final List<String> redisKeys = new ArrayList<>();

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
    @DisplayName("마감 임박순으로 나오고, 인원 0·기본가와 대표 이미지 URL이 채워진다")
    void list_closeAtOrder() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        save("양파 3kg", FRESH_FOOD_ID, 8000, now.plusDays(2));
        GroupPurchase sooner = save("대파 한 단", FRESH_FOOD_ID, 3000, now.plusDays(1));

        mockMvc.perform(get("/api/group-purchases").cookie(loginAndGetCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].title").value("대파 한 단"))
                .andExpect(jsonPath("$.data.content[0].currentHeadcount").value(0))
                .andExpect(jsonPath("$.data.content[0].currentPrice").value(3000))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl").value(endsWith("/images/" + sooner.getId() + ".png")))
                .andExpect(jsonPath("$.data.content[1].title").value("양파 3kg"));
    }
    @Test
    @DisplayName("카테고리로 걸러지고, 전체 개수와 페이지 수가 맞다")
    void list_categoryFilter_paging() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        save("대파 한 단", FRESH_FOOD_ID, 3000, now.plusDays(1));
        save("양파 3kg", FRESH_FOOD_ID, 8000, now.plusDays(2));
        save("감자 5kg", FRESH_FOOD_ID, 12000, now.plusDays(3));
        save("즉석밥 24개입", PROCESSED_FOOD_ID, 25000, now.plusDays(1));

        mockMvc.perform(get("/api/group-purchases")
                        .param("categoryId", String.valueOf(FRESH_FOOD_ID))
                        .param("size", "2")
                        .cookie(loginAndGetCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));

    }

    @Test
    @DisplayName("size가 50을 넘으면 400 INVALID_INPUT")
    void list_invalidSize() throws Exception {
        mockMvc.perform(get("/api/group-purchases")
                        .param("size", "51")
                        .cookie(loginAndGetCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    private GroupPurchase save(String title, long categoryId, int basePrice, LocalDateTime closeAt) {
        GroupPurchase groupPurchase = groupPurchaseRepository.save(GroupPurchase.builder()
                .seller(seller)
                .category(categoryRepository.getReferenceById(categoryId))
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

    private void setLiveValues(Long groupPurchaseId, String headcount, String price) {
        String headcountKey = GroupPurchaseRedisKeys.headcount(groupPurchaseId);
        String priceKey = GroupPurchaseRedisKeys.price(groupPurchaseId);
        redisTemplate.opsForValue().set(headcountKey, headcount);
        redisTemplate.opsForValue().set(priceKey, price);
        redisKeys.add(headcountKey);
        redisKeys.add(priceKey);
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
