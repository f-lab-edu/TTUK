package com.ttuk.grouppurchase;

import com.ttuk.category.Category;
import com.ttuk.category.CategoryRepository;
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

import java.time.Clock;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public class GroupPurchaseDetailIntegrationTest {

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
    GroupPurchaseImageRepository groupPurchaseImageRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    Clock clock;

    private Member seller;
    private Cookie buyerCookie;

    @BeforeEach
    void setUp() throws Exception {
        seller = memberRepository.save(member(SELLER_EMAIL, "진도농장", MemberRole.SELLER, "01011112222"));
        memberRepository.save(member(BUYER_EMAIL, "구매자", MemberRole.BUYER, "01033334444"));
        buyerCookie = loginAndGetCookie(BUYER_EMAIL);
    }

    @AfterEach
    void cleanUp() {
        groupPurchaseImageRepository.deleteAll();
        groupPurchaseRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("상세 조회하면 판매자·카테고리·할인 규칙과 이미지 URL이 등록 순서대로 내려간다")
    void detail_returnsInfoAndImagesInOrder() throws Exception {

        LocalDateTime closeAt = LocalDateTime.now(clock).plusDays(3).withNano(0);
        GroupPurchase groupPurchase = saveGroupPurchase(closeAt);

        saveImage(groupPurchase, "group-purchases/daepa-2.png", 1);
        saveImage(groupPurchase, "group-purchases/daepa-1.png", 0);
        saveImage(groupPurchase, "group-purchases/daepa-3.png", 2);

        mockMvc.perform(get("/api/group-purchases/{id}", groupPurchase.getId())
                        .cookie(buyerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupPurchaseId").value(groupPurchase.getId()))
                .andExpect(jsonPath("$.data.title").value("대파 한 단"))
                .andExpect(jsonPath("$.data.categoryName").value("신선식품"))
                .andExpect(jsonPath("$.data.sellerNickname").value("진도농장"))
                .andExpect(jsonPath("$.data.basePrice").value(3000))
                .andExpect(jsonPath("$.data.floorPrice").value(2500))
                .andExpect(jsonPath("$.data.discountType").value("RATE"))
                .andExpect(jsonPath("$.data.discountValue").value(5))
                .andExpect(jsonPath("$.data.discountStepSize").value(10))
                .andExpect(jsonPath("$.data.maxHeadcount").value(50))
                .andExpect(jsonPath("$.data.closeAt").value(closeAt.toString()))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.imageUrls.length()").value(3))
                .andExpect(jsonPath("$.data.imageUrls[0]").value(endsWith("daepa-1.png")))
                .andExpect(jsonPath("$.data.imageUrls[1]").value(endsWith("daepa-2.png")))
                .andExpect(jsonPath("$.data.imageUrls[2]").value(endsWith("daepa-3.png")));


    }

    @Test
    @DisplayName("없는 공동구매를 조회하면 404 GROUP_PURCHASE_NOT_FOUND를 반환한다")
    void detail_notFound() throws Exception {
        mockMvc.perform(get("/api/group-purchases/{id}", 999_999L)
                        .cookie(buyerCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROUP_PURCHASE_NOT_FOUND"));
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
                .closeAt(closeAt)
                .build());
    }

    private void saveImage(GroupPurchase groupPurchase, String imageKey, int sortOrder) {
        groupPurchaseImageRepository.save(GroupPurchaseImage.builder()
                .groupPurchase(groupPurchase)
                .imageKey(imageKey)
                .sortOrder(sortOrder)
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
