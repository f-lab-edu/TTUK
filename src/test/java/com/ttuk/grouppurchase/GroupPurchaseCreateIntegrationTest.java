package com.ttuk.grouppurchase;

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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public class GroupPurchaseCreateIntegrationTest {

    private static final String SELLER_EMAIL = "seller@ttuk.kr";
    private static final String BUYER_EMAIL = "quill@ttuk.kr";
    private static final String PASSWORD = "a123456789";
    private static final long FRESH_FOOD_ID = 1L;   // data.sql 첫 번째: 신선식품

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    GroupPurchaseRepository groupPurchaseRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private Member seller;

    @BeforeEach
    void setUp() {
        seller = memberRepository.save(Member.builder()
                .email(SELLER_EMAIL)
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
        groupPurchaseRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("등록 성공: 201, OPEN 상태로 저장되고 판매자·카테고리가 연결된다")
    void create_success() throws Exception {

        Cookie sellerCookie = loginAndGetCookie(SELLER_EMAIL);

        create(sellerCookie, json(FRESH_FOOD_ID, 2500, LocalDateTime.now().plusDays(3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.groupPurchaseId").isNumber())
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        List<GroupPurchase> saved = groupPurchaseRepository.findAll();
        assertThat(saved).hasSize(1);
        GroupPurchase groupPurchase = saved.get(0);

        assertThat(groupPurchase.getStatus()).isEqualTo(GroupPurchaseStatus.OPEN);
        assertThat(groupPurchase.getSeller().getId()).isEqualTo(seller.getId());
        assertThat(groupPurchase.getCategory().getId()).isEqualTo(FRESH_FOOD_ID);
        assertThat(groupPurchase.getFinalPrice()).isNull();
    }

    @Test
    @DisplayName("구매자가 등록하면 403 FORBIDDEN, 저장되지 않는다")
    void create_buyer_forbidden() throws Exception {

        memberRepository.save(Member.builder()
                .email(BUYER_EMAIL)
                .encodedPassword(passwordEncoder.encode(PASSWORD))
                .nickname("용현")
                .role(MemberRole.BUYER)
                .phoneNum("01012345678")
                .zipCode("06236")
                .address("서울시 강남구 테헤란로 123")
                .build());

        Cookie buyerCookie = loginAndGetCookie(BUYER_EMAIL);
        create(buyerCookie, json(FRESH_FOOD_ID, 2500, LocalDateTime.now().plusDays(3)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        assertThat(groupPurchaseRepository.count()).isZero();

    }

    @Test
    @DisplayName("필수 값(basePrice)이 없으면 400 INVALID_INPUT")
    void create_missingBasePrice() throws Exception {

        Cookie sellerCookie = loginAndGetCookie(SELLER_EMAIL);
        String json = """
                {
                  "categoryId": 1,
                  "title": "대파 한 단",
                  "unit": "단",
                  "floorPrice": 2500,
                  "discountType": "RATE",
                  "discountValue": 5,
                  "discountStepSize": 10,
                  "closeAt": "%s"
                }
                """.formatted(LocalDateTime.now().plusDays(3).withNano(0));

        create(sellerCookie, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        assertThat(groupPurchaseRepository.count()).isZero();

    }

    @Test
    @DisplayName("하한가가 기본가보다 크면 400 INVALID_DISCOUNT_RULE")
    void create_invalidDiscountRule() throws Exception {
        Cookie sellerCookie = loginAndGetCookie(SELLER_EMAIL);

        create(sellerCookie, json(FRESH_FOOD_ID, 3500, LocalDateTime.now().plusDays(3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_DISCOUNT_RULE"));

        assertThat(groupPurchaseRepository.count()).isZero();
    }

    @Test
    @DisplayName("마감 시각이 1시간도 안 남았으면 400 INVALID_CLOSE_AT")
    void create_invalidCloseAt() throws Exception {
        Cookie sellerCookie = loginAndGetCookie(SELLER_EMAIL);

        create(sellerCookie, json(FRESH_FOOD_ID, 2500, LocalDateTime.now().plusMinutes(30)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CLOSE_AT"));

        assertThat(groupPurchaseRepository.count()).isZero();
    }

    @Test
    @DisplayName("없는 카테고리면 404 CATEGORY_NOT_FOUND, 저장되지 않는다")
    void create_categoryNotFound() throws Exception {
        Cookie sellerCookie = loginAndGetCookie(SELLER_EMAIL);

        create(sellerCookie, json(999L, 2500, LocalDateTime.now().plusDays(3)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"));

        assertThat(groupPurchaseRepository.count()).isZero();
    }


    private ResultActions create(Cookie sessionCookie, String json) throws Exception {
        return mockMvc.perform(post("/api/group-purchases")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String json(long categoryId, int floorPrice, LocalDateTime closeAt) {
        return """
                {
                  "categoryId": %d,
                  "title": "대파 한 단",
                  "description": "진도 대파, 11/12 수확",
                  "unit": "단",
                  "basePrice": 3000,
                  "floorPrice": %d,
                  "discountType": "RATE",
                  "discountValue": 5,
                  "discountStepSize": 10,
                  "maxHeadcount": 50,
                  "closeAt": "%s"
                }
                """.formatted(categoryId, floorPrice, closeAt.withNano(0));
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
