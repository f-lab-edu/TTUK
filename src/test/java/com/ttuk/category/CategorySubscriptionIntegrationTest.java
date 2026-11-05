package com.ttuk.category;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CategorySubscriptionIntegrationTest {

    private static final String EMAIL = "quill@ttuk.kr";
    private static final String PASSWORD = "a123456789";
    private static final long FRESH_FOOD_ID = 1L;   // data.sql 첫 번째: 신선식품
    private static final String SELLER_EMAIL = "seller@ttuk.kr";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    MemberCategoryRepository memberCategoryRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private Member member;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
                .email(EMAIL)
                .encodedPassword(passwordEncoder.encode(PASSWORD))
                .nickname("용현")
                .role(MemberRole.BUYER)
                .phoneNum("01012345678")
                .zipCode("06236")
                .address("서울시 강남구 테헤란로 123")
                .build());
    }

    @AfterEach
    void cleanUp() {
        memberCategoryRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("구독 성공: 201, 구독 행이 저장된다")
    void subscribe_success() throws Exception {
        Cookie sessionCookie = loginAndGetCookie();

        subscribe(sessionCookie, "1").andExpect(status().isCreated());

        assertThat(memberCategoryRepository.existsByMemberIdAndCategoryId(member.getId(), FRESH_FOOD_ID)).isTrue();
    }

    @Test
    @DisplayName("이미 구독한 카테고리면 409 ALREADY_SUBSCRIBED, 행은 1개 그대로")
    void subscribe_alreadySubscribed() throws Exception {
        Cookie sessionCookie = loginAndGetCookie();
        subscribe(sessionCookie, "1").andExpect(status().isCreated());

        subscribe(sessionCookie, "1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_SUBSCRIBED"));

        assertThat(memberCategoryRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 카테고리면 404 CATEGORY_NOT_FOUND, 저장되지 않는다")
    void subscribe_categoryNotFound() throws Exception {
        Cookie sessionCookie = loginAndGetCookie();

        subscribe(sessionCookie, "999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"));

        assertThat(memberCategoryRepository.count()).isZero();
    }

    @Test
    @DisplayName("카테고리 id가 숫자가 아니면 400 INVALID_INPUT")
    void subscribe_invalidCategoryId() throws Exception {
        Cookie sessionCookie = loginAndGetCookie();

        subscribe(sessionCookie, "abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("로그인 없이 요청하면 401 UNAUTHORIZED")
    void subscribe_withoutLogin() throws Exception {
        mockMvc.perform(post("/api/categories/1/subscriptions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("같은 구독 요청 10개가 동시에 와도 1건만 저장되고 나머지는 409 (500 없음)")
    void subscribe_concurrentRequests() throws Exception {
        Cookie sessionCookie = loginAndGetCookie();
        int requestCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return subscribe(sessionCookie, "1").andReturn().getResponse().getStatus();
            }));
        }

        ready.await();
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        executor.shutdown();

        assertThat(statuses).containsOnly(201, 409);
        assertThat(Collections.frequency(statuses, 201)).isEqualTo(1);
        assertThat(memberCategoryRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("판매자가 구독하면 403 FORBIDDEN, 저장되지 않는다")
    void subscribe_seller_forbidden() throws Exception {
        memberRepository.save(Member.builder()
                .email(SELLER_EMAIL)
                .encodedPassword(passwordEncoder.encode(PASSWORD))
                .nickname("판매자")
                .role(MemberRole.SELLER)
                .phoneNum("01022223333")
                .zipCode("06236")
                .address("서울시 강남구 테헤란로 123")
                .build());
        Cookie sellerCookie = loginAndGetCookie(SELLER_EMAIL);

        subscribe(sellerCookie, "1")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertThat(memberCategoryRepository.count()).isZero();
    }

    private ResultActions subscribe(Cookie sessionCookie, String categoryId) throws Exception {
        return mockMvc.perform(post("/api/categories/" + categoryId + "/subscriptions")
                .cookie(sessionCookie));
    }

    private Cookie loginAndGetCookie() throws Exception {
        return loginAndGetCookie(EMAIL);
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