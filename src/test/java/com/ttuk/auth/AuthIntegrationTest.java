package com.ttuk.auth;

import com.ttuk.global.error.ErrorCode;
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
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuthIntegrationTest {

    private static final String EMAIL = "quill@ttuk.kr";
    private static final String PASSWORD = "a123456789";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    FindByIndexNameSessionRepository<? extends Session> sessionRepository;

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
        memberRepository.deleteAll();
    }


    @Test
    @DisplayName("로그인 성공: 200과 회원 정보, SESSION 쿠키를 돌려주고 Redis에 세션이 저장된다")
    void login_success() throws Exception {
        MvcResult result = login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(member.getId()))
                .andExpect(jsonPath("$.data.nickname").value("용현"))
                .andExpect(jsonPath("$.data.role").value("BUYER"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getCookie("SESSION")).isNotNull();
        assertThat(sessionsOf(member)).hasSize(1);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 INVALID_CREDENTIALS, 세션은 만들지 않는다")
    void login_wrongPassword() throws Exception {
        login(EMAIL, "wrong123456")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(cookie().doesNotExist("SESSION"));

        assertThat(sessionsOf(member)).isEmpty();
    }

    @Test
    @DisplayName("없는 이메일도 비밀번호 틀림과 같은 코드·메시지로 응답한다 (가입 여부 노출 방지)")
    void login_unknownEmail() throws Exception {
        login("nobody@ttuk.kr", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.INVALID_CREDENTIALS.getMessage()));
    }

    @Test
    @DisplayName("이메일이 비어 있으면 400 INVALID_INPUT")
    void login_blankEmail() throws Exception {
        login("", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.startsWith("email:")));
    }

    @Test
    @DisplayName("이미 세션이 있는 상태로 다시 로그인하면 새 세션 ID를 발급하고 기존 세션은 지운다")
    void login_again_issuesNewSession() throws Exception {
        Cookie oldCookie = loginAndGetCookie();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(oldCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie newCookie = result.getResponse().getCookie("SESSION");
        assertThat(newCookie.getValue()).isNotEqualTo(oldCookie.getValue());
        assertThat(sessionsOf(member)).hasSize(1);

        logout(oldCookie).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃 성공: 204, Redis에서 세션이 지워진다")
    void logout_success() throws Exception {
        Cookie sessionCookie = loginAndGetCookie();
        logout(sessionCookie).andExpect(status().isNoContent());
        assertThat(sessionsOf(member)).isEmpty();
    }

    @Test
    @DisplayName("로그인 없이 로그아웃하면 401 UNAUTHORIZED (인터셉터가 막음)")
    void logout_withoutLogin() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("로그아웃한 쿠키로 다시 로그아웃하면 401")
    void logout_twice() throws Exception {
        Cookie sessionCookie = loginAndGetCookie();
        logout(sessionCookie).andExpect(status().isNoContent());
        logout(sessionCookie).andExpect(status().isUnauthorized());
    }


    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, password)));
    }

    private ResultActions logout(Cookie sessionCookie) throws Exception {
        return mockMvc.perform(post("/api/auth/logout").cookie(sessionCookie));
    }

    private Cookie loginAndGetCookie() throws Exception {
        return login(EMAIL, PASSWORD).andReturn().getResponse().getCookie("SESSION");
    }

    private String loginJson(String email, String password) {
        return """
                { "email": "%s", "password": "%s" }
                """.formatted(email, password);
    }

    private Map<String, ? extends Session> sessionsOf(Member member) {
        return sessionRepository.findByPrincipalName(String.valueOf(member.getId()));
    }

}