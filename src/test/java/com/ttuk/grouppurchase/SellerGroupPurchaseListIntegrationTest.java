package com.ttuk.grouppurchase;

import com.ttuk.category.CategoryRepository;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisKeys;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class SellerGroupPurchaseListIntegrationTest {

    private static final String URL = "/api/members/me/group-purchases";
    private static final String SELLER_EMAIL = "seller@ttuk.kr";
    private static final String BUYER_A_EMAIL = "buyer-a@ttuk.kr";
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
    StringRedisTemplate redisTemplate;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    Clock clock;

    private Member seller;
    private Member buyerA;
    private Member buyerB;
    private LocalDateTime now;
    private final List<String> redisKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock);
        seller = saveMember(SELLER_EMAIL, "판매자", MemberRole.SELLER, "01022223333");
        buyerA = saveMember(BUYER_A_EMAIL, "구매자에이", MemberRole.BUYER, "01011110001");
        buyerB = saveMember("buyer-b@ttuk.kr", "구매자비", MemberRole.BUYER, "01011110002");
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(redisKeys);
        participationRepository.deleteAll();
        groupPurchaseRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("상태마다 인원·단가 출처와 합계가 다르다 — 정산 전은 CLOSED로 보이지만 Redis 값이고 금액은 없다")
    void list_eachStatus() throws Exception {
        GroupPurchase open = save("진행 중", now.plusDays(1));
        GroupPurchase pending = save("정산 전", now.minusMinutes(1));
        GroupPurchase closed = save("마감", now.minusDays(1));
        GroupPurchase canceled = save("취소", now.plusDays(1));
        finish(closed.getId(), GroupPurchaseStatus.CLOSED, 12, 2700);
        finish(canceled.getId(), GroupPurchaseStatus.CANCELED, 5, 2850);
        setLiveValues(open.getId(), "3", "2850");
        setLiveValues(pending.getId(), "5", "2700");
        join(open, buyerA, 2);
        join(pending, buyerA, 4);
        join(closed, buyerA, 3);
        join(closed, buyerB, 2);
        join(canceled, buyerA, 1);

        mockMvc.perform(get(URL).cookie(login(SELLER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content[0].title").value("취소"))
                .andExpect(jsonPath("$.data.content[0].status").value("CANCELED"))
                .andExpect(jsonPath("$.data.content[0].currentHeadcount").value(5))
                .andExpect(jsonPath("$.data.content[0].currentPrice").value(2850))
                .andExpect(jsonPath("$.data.content[0].totalQuantity").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].totalAmount").value(nullValue()))
                .andExpect(jsonPath("$.data.content[1].title").value("마감"))
                .andExpect(jsonPath("$.data.content[1].status").value("CLOSED"))
                .andExpect(jsonPath("$.data.content[1].currentHeadcount").value(12))
                .andExpect(jsonPath("$.data.content[1].currentPrice").value(2700))
                .andExpect(jsonPath("$.data.content[1].totalQuantity").value(5))
                .andExpect(jsonPath("$.data.content[1].totalAmount").value(13500))
                .andExpect(jsonPath("$.data.content[2].title").value("정산 전"))
                .andExpect(jsonPath("$.data.content[2].status").value("CLOSED"))
                .andExpect(jsonPath("$.data.content[2].currentHeadcount").value(5))
                .andExpect(jsonPath("$.data.content[2].currentPrice").value(2700))
                .andExpect(jsonPath("$.data.content[2].totalQuantity").value(4))
                .andExpect(jsonPath("$.data.content[2].totalAmount").value(nullValue()))
                .andExpect(jsonPath("$.data.content[3].title").value("진행 중"))
                .andExpect(jsonPath("$.data.content[3].status").value("OPEN"))
                .andExpect(jsonPath("$.data.content[3].currentHeadcount").value(3))
                .andExpect(jsonPath("$.data.content[3].currentPrice").value(2850))
                .andExpect(jsonPath("$.data.content[3].totalQuantity").value(2))
                .andExpect(jsonPath("$.data.content[3].totalAmount").value(nullValue()));
    }

    @Test
    @DisplayName("이번 페이지가 취소 건뿐이면 Redis·수량 합을 부르지 않고도 정상 응답한다")
    void list_onlyCanceled() throws Exception {
        GroupPurchase canceled = save("취소", now.plusDays(1));
        finish(canceled.getId(), GroupPurchaseStatus.CANCELED, 5, 2850);
        join(canceled, buyerA, 1);

        mockMvc.perform(get(URL)
                        .param("status", "CANCELED")
                        .cookie(login(SELLER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].currentHeadcount").value(5))
                .andExpect(jsonPath("$.data.content[0].totalQuantity").value(nullValue()));
    }

    @Test
    @DisplayName("구매자가 부르면 403 FORBIDDEN")
    void list_buyerForbidden() throws Exception {
        mockMvc.perform(get(URL).cookie(login(BUYER_A_EMAIL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("status가 없는 상태 값이면 400 INVALID_INPUT")
    void list_invalidStatus() throws Exception {
        mockMvc.perform(get(URL)
                        .param("status", "PAUSED")
                        .cookie(login(SELLER_EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
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

    private GroupPurchase save(String title, LocalDateTime closeAt) {
        return groupPurchaseRepository.save(GroupPurchase.builder()
                .seller(seller)
                .category(categoryRepository.getReferenceById(FRESH_FOOD_ID))
                .title(title)
                .unit("개")
                .basePrice(3000)
                .floorPrice(1500)
                .discountType(DiscountType.RATE)
                .discountValue(5)
                .discountStepSize(10)
                .maxHeadcount(30)
                .closeAt(closeAt)
                .build());
    }

    private void finish(Long groupPurchaseId, GroupPurchaseStatus status, int finalHeadcount, int finalPrice) {
        jdbcTemplate.update("""
                update group_purchase
                   set status = ?, final_headcount = ?, final_price = ?
                 where id = ?
                """, status.name(), finalHeadcount, finalPrice, groupPurchaseId);
    }

    private void join(GroupPurchase groupPurchase, Member buyer, int quantity) {
        participationRepository.save(Participation.builder()
                .groupPurchase(groupPurchase)
                .member(buyer)
                .quantity(quantity)
                .joinedAt(now)
                .build());
    }

    private void setLiveValues(Long groupPurchaseId, String headcount, String price) {
        String headcountKey = GroupPurchaseRedisKeys.headcount(groupPurchaseId);
        String priceKey = GroupPurchaseRedisKeys.price(groupPurchaseId);
        redisTemplate.opsForValue().set(headcountKey, headcount);
        redisTemplate.opsForValue().set(priceKey, price);
        redisKeys.add(headcountKey);
        redisKeys.add(priceKey);
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