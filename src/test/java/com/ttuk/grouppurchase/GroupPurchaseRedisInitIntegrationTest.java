package com.ttuk.grouppurchase;

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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public class GroupPurchaseRedisInitIntegrationTest {

    private static final String SELLER_EMAIL = "seller@ttuk.kr";
    private static final String PASSWORD = "a123456789";
    private static final long FRESH_FOOD_ID = 1L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");


    @Autowired
    MockMvc mockMvc;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    GroupPurchaseRepository groupPurchaseRepository;
    @Autowired
    GroupPurchaseRedisRepository groupPurchaseRedisRepository;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        memberRepository.save(Member.builder()
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
        Set<String> keys = redisTemplate.keys("group_purchase:*");
        redisTemplate.delete(keys);
        groupPurchaseRepository.deleteAll();
        memberRepository.deleteAll();
    }


    @Test
    @DisplayName("등록이 커밋되면 Redis에 공동구매 키 4개가 값·만료 시각과 함께 생성된다")
    void create_initializesRedisKeys() throws Exception {

        Cookie sellerCookie = loginAndGetCookie(SELLER_EMAIL);
        LocalDateTime closeAt = LocalDateTime.now().plusDays(3).withNano(0);

        mockMvc.perform(post("/api/group-purchases")
                        .cookie(sellerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(closeAt)))
                .andExpect(status().isCreated());

        Long id = groupPurchaseRepository.findAll().get(0).getId();
        long closeAtEpoch = toEpochSecond(closeAt);
        long keepUntilEpoch = toEpochSecond(closeAt.plusDays(1));

        Map<Object, Object> meta = redisTemplate.opsForHash().entries(GroupPurchaseRedisKeys.meta(id));
        assertThat(meta)
                .containsEntry("basePrice", "3000")
                .containsEntry("floorPrice", "2500")
                .containsEntry("discountType", "RATE")
                .containsEntry("discountValue", "5")
                .containsEntry("discountStepSize", "10")
                .containsEntry("maxHeadcount", "50")
                .containsEntry("closeAt", String.valueOf(closeAtEpoch));

        assertThat(redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.headcount(id))).isEqualTo("0");
        assertThat(redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.price(id))).isEqualTo("3000");
        assertThat(redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.status(id))).isEqualTo("OPEN");
        assertThat(redisTemplate.hasKey(GroupPurchaseRedisKeys.participants(id))).isFalse();

        assertExpiresAt(GroupPurchaseRedisKeys.status(id), closeAtEpoch);
        assertExpiresAt(GroupPurchaseRedisKeys.meta(id), keepUntilEpoch);
        assertExpiresAt(GroupPurchaseRedisKeys.headcount(id), keepUntilEpoch);
        assertExpiresAt(GroupPurchaseRedisKeys.price(id), keepUntilEpoch);

    }

    @Test
    @DisplayName("이미 초기화된 공동구매를 다시 초기화하면 false, 진행 중인 인원을 덮어쓰지 않는다")
    void initialize_twice_doesNotOverwrite() {
        GroupPurchaseCreatedEvent event = new GroupPurchaseCreatedEvent(
                999L, 1L, "대파 한 단", 3000, 2500, DiscountType.RATE, 5, 10, 50,
                LocalDateTime.now().plusDays(3).withNano(0));

        assertThat(groupPurchaseRedisRepository.initialize(event)).isTrue();
        redisTemplate.opsForValue().increment(GroupPurchaseRedisKeys.headcount(999L), 3);

        boolean createdAgain = groupPurchaseRedisRepository.initialize(event);

        assertThat(createdAgain).isFalse();
        assertThat(redisTemplate.opsForValue().get(GroupPurchaseRedisKeys.headcount(999L))).isEqualTo("3");
    }


    private void assertExpiresAt(String key, long expectedEpochSecond){
        long expectedTtl = expectedEpochSecond - Instant.now().getEpochSecond();
        assertThat(redisTemplate.getExpire(key)).isCloseTo(expectedTtl, within(5L));
    }

    private long toEpochSecond(LocalDateTime dateTime) {
        return dateTime.atZone(ZONE).toEpochSecond();
    }

    private String json(LocalDateTime closeAt) {
        return """
                {
                  "categoryId": %d,
                  "title": "대파 한 단",
                  "description": "진도 대파, 11/12 수확",
                  "unit": "단",
                  "basePrice": 3000,
                  "floorPrice": 2500,
                  "discountType": "RATE",
                  "discountValue": 5,
                  "discountStepSize": 10,
                  "maxHeadcount": 50,
                  "closeAt": "%s"
                }
                """.formatted(FRESH_FOOD_ID, closeAt);
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
