package com.ttuk.member;

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
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public class MemberPasswordChangeIntegrationTest {

    private static final String EMAIL = "quill@ttuk.kr";
    private static final String OLD_PASSWORD = "a123456789";
    private static final String NEW_PASSWORD = "b123456789";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    @Autowired
    StringRedisTemplate redisTemplate;

    private Member member;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
                .email(EMAIL)
                .encodedPassword(passwordEncoder.encode(OLD_PASSWORD))
                .nickname("용현")
                .role(MemberRole.BUYER)
                .phoneNum("01012345678")
                .zipCode("06236")
                .address("서울시 강남구 테헤란로 123")
                .build());
    }

    @AfterEach
    void cleanUp() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("비밀번호 변경 성공: 204, 변경한 기기와 다른 기기의 세션이 모두 끝난다")
    void changePassword_success_expiresAllSessions() throws Exception {

        Cookie deviceA = loginAndGetCookie(OLD_PASSWORD);
        Cookie deviceB = loginAndGetCookie(OLD_PASSWORD);
        assertThat(sessionsOf(member)).hasSize(2);

        changePassword(deviceA, OLD_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("SESSION", 0));

        assertThat(sessionsOf(member)).isEmpty();
        logout(deviceA).andExpect(status().isUnauthorized());
        logout(deviceB).andExpect(status().isUnauthorized());

    }

    @Test
    @DisplayName("비밀번호 변경 후 옛 비밀번호로는 로그인이 안 되고 새 비밀번호로는 된다")
    void changePassword_success_onlyNewPasswordWorks() throws Exception {

        Cookie sessionCookie = loginAndGetCookie(OLD_PASSWORD);
        changePassword(sessionCookie, OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        login(OLD_PASSWORD).andExpect(status().isUnauthorized());
        login(NEW_PASSWORD).andExpect(status().isOk());

        Member saved = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, saved.getPassword())).isTrue();

    }

    @Test
    @DisplayName("변경을 요청한 기기의 세션 키가 Redis에 다시 생기지 않는다 (invalidate 확인)")
    void changePassword_currentSessionNotRecreated() throws Exception {

        Cookie sessionCookie = loginAndGetCookie(OLD_PASSWORD);
        String sessionId = new String(Base64.getDecoder().decode(sessionCookie.getValue()));

        changePassword(sessionCookie, OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(redisTemplate.hasKey("spring:session:session:" + sessionId)).isFalse();

    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 400 INVALID_CURRENT_PASSWORD, 로그인은 유지된다")
    void changePassword_wrongCurrentPassword() throws Exception {
        Cookie sessionCookie = loginAndGetCookie(OLD_PASSWORD);

        changePassword(sessionCookie, "wrong123456", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CURRENT_PASSWORD"));

        assertThat(sessionsOf(member)).hasSize(1);
        login(OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("새 비밀번호가 현재와 같으면 400 SAME_AS_CURRENT_PASSWORD, 로그인은 유지된다")
    void changePassword_samePassword() throws Exception {
        Cookie sessionCookie = loginAndGetCookie(OLD_PASSWORD);

        changePassword(sessionCookie, OLD_PASSWORD, OLD_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SAME_AS_CURRENT_PASSWORD"));

        assertThat(sessionsOf(member)).hasSize(1);
    }

    @Test
    @DisplayName("새 비밀번호가 규칙(영문+숫자 10~30자)에 맞지 않으면 400 INVALID_INPUT")
    void changePassword_invalidNewPassword() throws Exception {
        Cookie sessionCookie = loginAndGetCookie(OLD_PASSWORD);

        changePassword(sessionCookie, OLD_PASSWORD, "abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value(startsWith("newPassword:")));
    }

    @Test
    @DisplayName("로그인 없이 요청하면 401 UNAUTHORIZED")
    void changePassword_withoutLogin() throws Exception {
        mockMvc.perform(patch("/api/members/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }






    private ResultActions login(String password) throws Exception {
        String json = """
                { "email": "%s", "password": "%s" }
                """.formatted(EMAIL, password);
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private Cookie loginAndGetCookie(String password) throws Exception {
        return login(password).andReturn().getResponse().getCookie("SESSION");
    }

    private ResultActions logout(Cookie sessionCookie) throws Exception {
        return mockMvc.perform(post("/api/auth/logout").cookie(sessionCookie));
    }

    private ResultActions changePassword(Cookie sessionCookie, String currentPassword, String newPassword)
            throws Exception {
        return mockMvc.perform(patch("/api/members/me/password")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(passwordJson(currentPassword, newPassword)));
    }

    private String passwordJson(String currentPassword, String newPassword) {
        return """
                { "currentPassword": "%s", "newPassword": "%s" }
                """.formatted(currentPassword, newPassword);
    }

    private Map<String, ? extends Session> sessionsOf(Member member) {
        return sessionRepository.findByPrincipalName(String.valueOf(member.getId()));
    }


}
