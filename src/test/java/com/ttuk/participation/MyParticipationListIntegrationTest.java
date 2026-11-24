package com.ttuk.participation;

import com.ttuk.category.CategoryRepository;
import com.ttuk.grouppurchase.DiscountType;
import com.ttuk.grouppurchase.GroupPurchase;
import com.ttuk.grouppurchase.GroupPurchaseRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
class MyParticipationListIntegrationTest {

    private static final String URL = "/api/members/me/participations";
    private static final String BUYER_EMAIL = "buyer@ttuk.kr";
    private static final String SELLER_EMAIL = "seller@ttuk.kr";
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
    private Member buyer;
    private LocalDateTime now;
    private final List<String> redisKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock).withNano(0);
        seller = saveMember(SELLER_EMAIL, "판매자", MemberRole.SELLER, "01022223333");
        buyer = saveMember(BUYER_EMAIL, "구매자", MemberRole.BUYER, "01011110001");
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(redisKeys);
        participationRepository.deleteAll();
        groupPurchaseRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("상태마다 가격·금액·취소 정보가 다르다 — 정산 전은 CONFIRMED지만 Redis 단가, 취소는 구매자·판매자·시스템으로 구분된다")
    void list_eachStatus() throws Exception {
        GroupPurchase leftGp = save("양파 3kg", "봉", now.plusDays(2));
        Participation left = join(leftGp, 1);
        participationRepository.cancelJoined(leftGp.getId(), buyer.getId(), now);

        GroupPurchase openGp = save("대파 한 단", "단", now.plusDays(2));
        Participation open = join(openGp, 1);
        setLiveValues(openGp.getId(), "3", "2850");

        GroupPurchase pendingGp = save("감자 5kg", "박스", now.minusMinutes(1));
        join(pendingGp, 2);
        setLiveValues(pendingGp.getId(), "5", "2700");

        GroupPurchase settledGp = save("배추 3포기", "망", now.minusDays(1));
        join(settledGp, 3);
        jdbcTemplate.update("""
                update group_purchase set status = 'CLOSED', final_headcount = 12, final_price = 2700
                 where id = ?
                """, settledGp.getId());

        GroupPurchase sellerCanceledGp = save("무 2개", "개", now.plusDays(2));
        join(sellerCanceledGp, 1);
        LocalDateTime sellerCanceledAt = now.minusHours(1);
        jdbcTemplate.update("""
                update group_purchase set status = 'CANCELED', final_headcount = 4, final_price = 2900,
                       cancel_reason = '재고 부족', canceled_at = ?
                 where id = ?
                """, sellerCanceledAt, sellerCanceledGp.getId());

        GroupPurchase systemGp = save("당근 1kg", "봉", now.minusDays(1));
        Participation system = join(systemGp, 1);
        LocalDateTime systemCanceledAt = now.minusHours(2);
        jdbcTemplate.update("""
                update participation set status = 'CANCELED', canceled_by = 'SYSTEM', canceled_at = ?
                 where id = ?
                """, systemCanceledAt, system.getId());

        mockMvc.perform(get(URL).cookie(login(BUYER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(6))
                // [0] 시스템 정리
                .andExpect(jsonPath("$.data.content[0].status").value("CANCELED"))
                .andExpect(jsonPath("$.data.content[0].canceledBy").value("SYSTEM"))
                .andExpect(jsonPath("$.data.content[0].cancelReason").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].canceledAt").value(iso(systemCanceledAt)))
                .andExpect(jsonPath("$.data.content[0].currentPrice").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].finalPrice").value(nullValue()))
                // [1] 판매자 취소
                .andExpect(jsonPath("$.data.content[1].status").value("CANCELED"))
                .andExpect(jsonPath("$.data.content[1].canceledBy").value("SELLER"))
                .andExpect(jsonPath("$.data.content[1].cancelReason").value("재고 부족"))
                .andExpect(jsonPath("$.data.content[1].canceledAt").value(iso(sellerCanceledAt)))
                .andExpect(jsonPath("$.data.content[1].finalPrice").value(nullValue()))
                // [2] 정산됨
                .andExpect(jsonPath("$.data.content[2].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.content[2].quantity").value(3))
                .andExpect(jsonPath("$.data.content[2].currentPrice").value(nullValue()))
                .andExpect(jsonPath("$.data.content[2].finalPrice").value(2700))
                .andExpect(jsonPath("$.data.content[2].amount").value(8100))
                .andExpect(jsonPath("$.data.content[2].canceledBy").value(nullValue()))
                // [3] 정산 전
                .andExpect(jsonPath("$.data.content[3].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.content[3].currentPrice").value(2700))
                .andExpect(jsonPath("$.data.content[3].finalPrice").value(nullValue()))
                .andExpect(jsonPath("$.data.content[3].amount").value(nullValue()))
                // [4] 진행 중 — 공통 칸 매핑
                .andExpect(jsonPath("$.data.content[4].participationId").value(open.getId()))
                .andExpect(jsonPath("$.data.content[4].groupPurchaseId").value(openGp.getId()))
                .andExpect(jsonPath("$.data.content[4].title").value("대파 한 단"))
                .andExpect(jsonPath("$.data.content[4].unit").value("단"))
                .andExpect(jsonPath("$.data.content[4].status").value("OPEN"))
                .andExpect(jsonPath("$.data.content[4].quantity").value(1))
                .andExpect(jsonPath("$.data.content[4].currentPrice").value(2850))
                .andExpect(jsonPath("$.data.content[4].amount").value(nullValue()))
                .andExpect(jsonPath("$.data.content[4].closeAt").value(iso(now.plusDays(2))))
                .andExpect(jsonPath("$.data.content[4].joinedAt").value(iso(now)))
                // [5] 구매자 취소
                .andExpect(jsonPath("$.data.content[5].participationId").value(left.getId()))
                .andExpect(jsonPath("$.data.content[5].status").value("CANCELED"))
                .andExpect(jsonPath("$.data.content[5].canceledBy").value("BUYER"))
                .andExpect(jsonPath("$.data.content[5].cancelReason").value(nullValue()))
                .andExpect(jsonPath("$.data.content[5].canceledAt").value(iso(now)));
    }

    @Test
    @DisplayName("판매자가 부르면 403 FORBIDDEN")
    void list_sellerForbidden() throws Exception {
        mockMvc.perform(get(URL).cookie(login(SELLER_EMAIL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("status가 없는 값이면 400 INVALID_INPUT")
    void list_invalidStatus() throws Exception {
        mockMvc.perform(get(URL)
                        .param("status", "PAUSED")
                        .cookie(login(BUYER_EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    private String iso(LocalDateTime time) {
        return time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
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

    private GroupPurchase save(String title, String unit, LocalDateTime closeAt) {
        return groupPurchaseRepository.save(GroupPurchase.builder()
                .seller(seller)
                .category(categoryRepository.getReferenceById(FRESH_FOOD_ID))
                .title(title)
                .unit(unit)
                .basePrice(3000)
                .floorPrice(1500)
                .discountType(DiscountType.RATE)
                .discountValue(5)
                .discountStepSize(10)
                .maxHeadcount(30)
                .closeAt(closeAt)
                .build());
    }

    private Participation join(GroupPurchase groupPurchase, int quantity) {
        return participationRepository.save(Participation.builder()
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