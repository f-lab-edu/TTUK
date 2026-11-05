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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CategoryUnsubscribeIntegrationTest {

    private static final String EMAIL = "quill@ttuk.kr";
    private static final String OTHER_EMAIL = "other@ttuk.kr";
    private static final String PASSWORD = "a123456789";
    private static final long FRESH_FOOD_ID = 1L;   // data.sql 첫 번째: 신선식품

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
        member = saveMember(EMAIL, "용현", "01012345678");
    }

    @AfterEach
    void cleanUp() {
        memberCategoryRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("해제 성공: 204, 구독 행이 삭제된다")
    void unsubscribe_success() throws Exception {
        Cookie sessionCookie = loginAndGetCookie(EMAIL);

        subscribe(sessionCookie, FRESH_FOOD_ID);

        unsubscribe(sessionCookie, String.valueOf(FRESH_FOOD_ID)).andExpect(status().isNoContent());

        assertThat(memberCategoryRepository.existsByMemberIdAndCategoryId(member.getId(), FRESH_FOOD_ID)).isFalse();

    }

    @Test
    @DisplayName("구독하지 않은 카테고리를 해제하면 404 SUBSCRIPTION_NOT_FOUND")
    void unsubscribe_notSubscribed() throws Exception {

        Cookie sessionCookie = loginAndGetCookie(EMAIL);

        unsubscribe(sessionCookie, String.valueOf(FRESH_FOOD_ID)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUBSCRIPTION_NOT_FOUND"));

    }

    @Test
    @DisplayName("같은 카테고리를 두 번 해제하면 첫 번째는 204, 두 번째는 404")
    void unsubscribe_twice() throws Exception {
        Cookie sessionCookie = loginAndGetCookie(EMAIL);
        subscribe(sessionCookie, FRESH_FOOD_ID);

        unsubscribe(sessionCookie, "1").andExpect(status().isNoContent());
        unsubscribe(sessionCookie, "1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUBSCRIPTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("없는 카테고리를 해제하면 404 SUBSCRIPTION_NOT_FOUND")
    void unsubscribe_categoryNotFound() throws Exception {

        Cookie sessionCookie = loginAndGetCookie(EMAIL);

        unsubscribe(sessionCookie, String.valueOf(9999)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUBSCRIPTION_NOT_FOUND"));

    }

    @Test
    @DisplayName("카테고리 id가 숫자가 아니면 400 INVALID_INPUT")
    void unsubscribe_invalidCategoryId() throws Exception {
        Cookie sessionCookie = loginAndGetCookie(EMAIL);

        unsubscribe(sessionCookie, "abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("로그인 없이 요청하면 401 UNAUTHORIZED")
    void unsubscribe_withoutLogin() throws Exception {
        mockMvc.perform(delete("/api/categories/1/subscriptions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("내 해제 요청은 다른 회원의 같은 카테고리 구독을 지우지 않는다")
    void unsubscribe_doesNotDeleteOtherMemberSubscription() throws Exception {

        Member other = saveMember(OTHER_EMAIL, "회원3", "01055551010");
        subscribe(loginAndGetCookie(OTHER_EMAIL), FRESH_FOOD_ID);

        unsubscribe(loginAndGetCookie(EMAIL), "1")
                .andExpect(status().isNotFound());

        assertThat(memberCategoryRepository.existsByMemberIdAndCategoryId(other.getId(), FRESH_FOOD_ID)).isTrue();

    }

    @Test
    @DisplayName("해제하면 목록 조회에서 바로 subscribed가 false가 된다")
    void unsubscribe_reflectedInCategoryList() throws Exception {
        Cookie sessionCookie = loginAndGetCookie(EMAIL);
        subscribe(sessionCookie, FRESH_FOOD_ID);

        mockMvc.perform(get("/api/categories").cookie(sessionCookie))
                .andExpect(jsonPath("$.data[0].subscribed").value(true));

        unsubscribe(sessionCookie, "1").andExpect(status().isNoContent());

        mockMvc.perform(get("/api/categories").cookie(sessionCookie))
                .andExpect(jsonPath("$.data[0].subscribed").value(false));
    }

    @Test
    @DisplayName("같은 해제 요청 10개가 동시에 와도 1개만 204, 나머지는 404 (500 없음)")
    void unsubscribe_concurrentRequests() throws Exception {
        Cookie sessionCookie = loginAndGetCookie(EMAIL);
        subscribe(sessionCookie, FRESH_FOOD_ID);
        int requestCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return unsubscribe(sessionCookie, "1").andReturn().getResponse().getStatus();
            }));
        }

        ready.await();
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        executor.shutdown();

        assertThat(statuses).containsOnly(204, 404);
        assertThat(Collections.frequency(statuses, 204)).isEqualTo(1);
        assertThat(memberCategoryRepository.count()).isZero();
    }

    @Test
    @DisplayName("판매자가 해제하면 403 FORBIDDEN (서비스까지 가지 않아 404가 아니라 403)")
    void unsubscribe_seller_forbidden() throws Exception {
        memberRepository.save(Member.builder()
                .email("seller@ttuk.kr")
                .encodedPassword(passwordEncoder.encode(PASSWORD))
                .nickname("판매자")
                .role(MemberRole.SELLER)
                .phoneNum("01022223333")
                .zipCode("06236")
                .address("서울시 강남구 테헤란로 123")
                .build());
        Cookie sellerCookie = loginAndGetCookie("seller@ttuk.kr");

        unsubscribe(sellerCookie, "1")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }



    private ResultActions unsubscribe(Cookie sessionCookie, String categoryId) throws Exception {
        return mockMvc.perform(delete("/api/categories/" + categoryId + "/subscriptions")
                .cookie(sessionCookie));
    }

    private void subscribe(Cookie sessionCookie, Long categoryId) throws Exception {
        mockMvc.perform(post("/api/categories/" + categoryId + "/subscriptions").cookie(sessionCookie))
                .andExpect(status().isCreated());
    }

    private Member saveMember(String email, String nickname, String phoneNum) {
        return memberRepository.save(Member.builder()
                .email(email)
                .encodedPassword(passwordEncoder.encode(PASSWORD))
                .nickname(nickname)
                .role(MemberRole.BUYER)
                .phoneNum(phoneNum)
                .zipCode("06236")
                .address("서울시 강남구 테헤란로 123")
                .build());
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
