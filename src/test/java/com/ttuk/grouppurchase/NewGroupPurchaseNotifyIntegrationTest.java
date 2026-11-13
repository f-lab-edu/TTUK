package com.ttuk.grouppurchase;

import com.ttuk.member.Member;
import com.ttuk.member.MemberRepository;
import com.ttuk.member.MemberRole;
import com.ttuk.notification.NotificationStreamRepository;
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
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ttuk.support.TestMultipartFiles;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;


@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public class NewGroupPurchaseNotifyIntegrationTest {

    private static final String SELLER_EMAIL = "seller@ttuk.kr";
    private static final String PASSWORD = "a123456789";
    private static final long FRESH_FOOD_ID = 1L;   // data.sql 첫 번째: 신선식품

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    GroupPurchaseRepository groupPurchaseRepository;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    GroupPurchaseImageRepository groupPurchaseImageRepository;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(NotificationStreamRepository.STREAM_KEY);
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
        redisTemplate.delete(NotificationStreamRepository.STREAM_KEY);
        Set<String> keys = redisTemplate.keys("group_purchase:*");
        redisTemplate.delete(keys);
        groupPurchaseImageRepository.deleteAll();
        groupPurchaseRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("등록이 커밋되면 알림 Stream에 새 공동구매 메시지가 1건 추가된다")
    void create_addsNewGroupPurchaseMessage() throws Exception {

        Cookie sellerCookie = loginAndGetCookie(SELLER_EMAIL);

        mockMvc.perform(multipart("/api/group-purchases")
                        .file(TestMultipartFiles.requestPart(json(LocalDateTime.now().plusDays(3).withNano(0))))
                        .file(TestMultipartFiles.pngImage("daepa.png"))
                        .cookie(sellerCookie))
                .andExpect(status().isCreated());

        Long id = groupPurchaseRepository.findAll().get(0).getId();
        List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().range(NotificationStreamRepository.STREAM_KEY, Range.unbounded());

        assertThat(messages).hasSize(1);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getValue())
                .containsEntry("type", "NEW_GROUP_PURCHASE")
                .containsEntry("groupPurchaseId", String.valueOf(id))
                .containsEntry("categoryId", String.valueOf(FRESH_FOOD_ID))
                .containsEntry("title", "대파 한 단");

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
