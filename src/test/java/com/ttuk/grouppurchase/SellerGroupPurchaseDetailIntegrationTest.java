package com.ttuk.grouppurchase;

import com.ttuk.category.CategoryRepository;
import com.ttuk.member.Member;
import com.ttuk.member.MemberRepository;
import com.ttuk.member.MemberRole;
import com.ttuk.participation.Participation;
import com.ttuk.participation.ParticipationRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class SellerGroupPurchaseDetailIntegrationTest {

    private static final String URL = "/api/members/me/group-purchases/{groupPurchaseId}";
    private static final String SELLER_EMAIL = "seller@ttuk.kr";
    private static final String OTHER_SELLER_EMAIL = "other@ttuk.kr";
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
    PasswordEncoder passwordEncoder;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    Clock clock;

    private Member seller;
    private Member buyerA;
    private Member buyerB;
    private LocalDateTime closeAt;

    @BeforeEach
    void setUp() {
        closeAt = LocalDateTime.now(clock).minusDays(1).withSecond(30).withNano(0);
        seller = saveMember(SELLER_EMAIL, "판매자", MemberRole.SELLER, "01022223333");
        saveMember(OTHER_SELLER_EMAIL, "다른판매자", MemberRole.SELLER, "01044445555");
        buyerA = saveMember("buyer-a@ttuk.kr", "구매자에이", MemberRole.BUYER, "01011110001");
        buyerB = saveMember("buyer-b@ttuk.kr", "구매자비", MemberRole.BUYER, "01011110002");
    }

    @AfterEach
    void cleanUp() {
        participationRepository.deleteAll();
        groupPurchaseRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("정산된 공동구매는 모든 칸이 제자리에 나오고, 확정 인원·단가와 수량·금액이 채워진다")
    void detail_closed() throws Exception {
        GroupPurchase saved = save();
        LocalDateTime closedAt = closeAt.minusHours(2);
        jdbcTemplate.update("""
                update group_purchase
                   set status = 'CLOSED', final_headcount = 12, final_price = 2700, closed_at = ?
                 where id = ?
                """, closedAt, saved.getId());
        join(saved, buyerA, 3);
        join(saved, buyerB, 2);
        String categoryName = jdbcTemplate.queryForObject(
                "select name from category where id = ?", String.class, FRESH_FOOD_ID);

        mockMvc.perform(get(URL, saved.getId()).cookie(login(SELLER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupPurchaseId").value(saved.getId()))
                .andExpect(jsonPath("$.data.title").value("대파 한 단"))
                .andExpect(jsonPath("$.data.categoryName").value(categoryName))
                .andExpect(jsonPath("$.data.unit").value("단"))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.basePrice").value(3000))
                .andExpect(jsonPath("$.data.floorPrice").value(1500))
                .andExpect(jsonPath("$.data.discountType").value("RATE"))
                .andExpect(jsonPath("$.data.discountValue").value(5))
                .andExpect(jsonPath("$.data.discountStepSize").value(10))
                .andExpect(jsonPath("$.data.maxHeadcount").value(30))
                .andExpect(jsonPath("$.data.currentHeadcount").value(12))
                .andExpect(jsonPath("$.data.currentPrice").value(2700))
                .andExpect(jsonPath("$.data.totalQuantity").value(5))
                .andExpect(jsonPath("$.data.totalAmount").value(13500))
                .andExpect(jsonPath("$.data.closeAt").value(closeAt.toString()))
                .andExpect(jsonPath("$.data.closedAt").value(closedAt.toString()))
                .andExpect(jsonPath("$.data.cancelReason").value(nullValue()))
                .andExpect(jsonPath("$.data.canceledAt").value(nullValue()))
                .andExpect(jsonPath("$.data.sellerId").doesNotExist());
    }

    @Test
    @DisplayName("취소된 공동구매는 취소 사유·시각이 나오고, 참여 행이 남아 있어도 수량·금액은 없다")
    void detail_canceled() throws Exception {
        GroupPurchase saved = save();
        LocalDateTime canceledAt = closeAt.minusHours(5);
        jdbcTemplate.update("""
                update group_purchase
                   set status = 'CANCELED', final_headcount = 5, final_price = 2850,
                       cancel_reason = '재고 부족', canceled_at = ?
                 where id = ?
                """, canceledAt, saved.getId());
        join(saved, buyerA, 1);

        mockMvc.perform(get(URL, saved.getId()).cookie(login(SELLER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.currentHeadcount").value(5))
                .andExpect(jsonPath("$.data.currentPrice").value(2850))
                .andExpect(jsonPath("$.data.totalQuantity").value(nullValue()))
                .andExpect(jsonPath("$.data.totalAmount").value(nullValue()))
                .andExpect(jsonPath("$.data.cancelReason").value("재고 부족"))
                .andExpect(jsonPath("$.data.canceledAt").value(canceledAt.toString()))
                .andExpect(jsonPath("$.data.closedAt").value(nullValue()));
    }

    @Test
    @DisplayName("다른 판매자의 공동구매면 403 NOT_GROUP_PURCHASE_OWNER")
    void detail_notOwner() throws Exception {
        GroupPurchase saved = save();

        mockMvc.perform(get(URL, saved.getId()).cookie(login(OTHER_SELLER_EMAIL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_PURCHASE_OWNER"));
    }

    @Test
    @DisplayName("없는 공동구매면 404 GROUP_PURCHASE_NOT_FOUND")
    void detail_notFound() throws Exception {
        mockMvc.perform(get(URL, Long.MAX_VALUE).cookie(login(SELLER_EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROUP_PURCHASE_NOT_FOUND"));
    }

    private Member saveMember(String email, String nickname, MemberRole role, String phoneNum) {
        return memberRepository.save(Member.builder()
                .email(email)
                .encodedPassword(passwordEncoder.encode(PASSWORD))
                .nickname(nickname)
                .role(role)
                .phoneNum(phoneNum)
                .zipCode("06236")
                .address("서울시 강남구 테헤란로 123")
                .build());
    }

    private GroupPurchase save() {
        return groupPurchaseRepository.save(GroupPurchase.builder()
                .seller(seller)
                .category(categoryRepository.getReferenceById(FRESH_FOOD_ID))
                .title("대파 한 단")
                .unit("단")
                .basePrice(3000)
                .floorPrice(1500)
                .discountType(DiscountType.RATE)
                .discountValue(5)
                .discountStepSize(10)
                .maxHeadcount(30)
                .closeAt(closeAt)
                .build());
    }

    private void join(GroupPurchase groupPurchase, Member buyer, int quantity) {
        participationRepository.save(Participation.builder()
                .groupPurchase(groupPurchase)
                .member(buyer)
                .quantity(quantity)
                .joinedAt(closeAt.minusDays(1))
                .build());
    }

    private Cookie login(String email) throws Exception {
        String json = """
                { "email": "%s", "password": "%s" }
                """.formatted(email, PASSWORD);
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn().getResponse().getCookie("SESSION");
    }
}