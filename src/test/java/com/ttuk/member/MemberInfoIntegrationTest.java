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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class MemberInfoIntegrationTest {

    private static final String EMAIL = "quill@ttuk.kr";
    private static final String PASSWORD = "a123456789";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private Member member;
    private Cookie sessionCookie;

    @BeforeEach
    void setUp() throws Exception {
        member = memberRepository.save(Member.builder()
                .email(EMAIL)
                .encodedPassword(passwordEncoder.encode(PASSWORD))
                .nickname("용현")
                .role(MemberRole.BUYER)
                .phoneNum("01012345678")
                .zipCode("06236")
                .address("서울시 강남구 테헤란로 123")
                .addressDetail("4층 401호")
                .build());

        sessionCookie = loginAndGetCookie();
    }

    @AfterEach
    void cleanUp() {
        memberRepository.deleteAll();
    }


    @Test
    @DisplayName("내 정보 조회 성공: 200, 비밀번호는 응답에 없고 가입일은 초 단위까지만 나온다")
    void getMyInfo_success() throws Exception {
        getMyInfo(sessionCookie)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(member.getId()))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.nickname").value("용현"))
                .andExpect(jsonPath("$.data.role").value("BUYER"))
                .andExpect(jsonPath("$.data.phoneNum").value("01012345678"))
                .andExpect(jsonPath("$.data.zipCode").value("06236"))
                .andExpect(jsonPath("$.data.address").value("서울시 강남구 테헤란로 123"))
                .andExpect(jsonPath("$.data.addressDetail").value("4층 401호"))
                .andExpect(jsonPath("$.data.createdAt")
                        .value(matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$")))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("로그인 없이 내 정보를 조회하면 401")
    void getMyInfo_withoutLogin() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }




    @Test
    @DisplayName("닉네임만 보내면 닉네임만 바뀌고 나머지는 그대로다")
    void updateMyInfo_nicknameOnly() throws Exception {
        updateMyInfo(sessionCookie, """
                { "nickname": "용현2" }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("용현2"))
                .andExpect(jsonPath("$.data.phoneNum").value("01012345678"))
                .andExpect(jsonPath("$.data.addressDetail").value("4층 401호"));

        Member saved = findSavedMember();
        assertThat(saved.getNickname()).isEqualTo("용현2");
        assertThat(saved.getPhoneNum()).isEqualTo("01012345678");
        assertThat(saved.getAddress()).isEqualTo("서울시 강남구 테헤란로 123");
    }

    @Test
    @DisplayName("연락처는 하이픈을 넣어 보내도 숫자만 저장된다")
    void updateMyInfo_phoneNumNormalized() throws Exception {
        updateMyInfo(sessionCookie, """
                { "phoneNum": "010-9876-5432" }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneNum").value("01098765432"));

        assertThat(findSavedMember().getPhoneNum()).isEqualTo("01098765432");
    }

    @Test
    @DisplayName("주소 묶음을 보내면 우편번호·주소·상세주소가 함께 바뀐다")
    void updateMyInfo_address() throws Exception {
        updateMyInfo(sessionCookie, """
                { "address": { "zipCode": "04524", "address": "서울시 중구 세종대로 110", "addressDetail": "2층" } }
                """)
                .andExpect(status().isOk());

        Member saved = findSavedMember();
        assertThat(saved.getZipCode()).isEqualTo("04524");
        assertThat(saved.getAddress()).isEqualTo("서울시 중구 세종대로 110");
        assertThat(saved.getAddressDetail()).isEqualTo("2층");
    }

    @Test
    @DisplayName("주소 묶음에 상세주소가 없으면 상세주소가 비워진다")
    void updateMyInfo_addressWithoutDetail_clearsDetail() throws Exception {
        updateMyInfo(sessionCookie, """
                { "address": { "zipCode": "04524", "address": "서울시 중구 세종대로 110" } }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressDetail").doesNotExist());

        assertThat(findSavedMember().getAddressDetail()).isNull();
    }

    @Test
    @DisplayName("이메일을 보내도 무시되고 바뀌지 않는다")
    void updateMyInfo_emailIgnored() throws Exception {
        updateMyInfo(sessionCookie, """
                { "email": "hacker@ttuk.kr", "nickname": "용현2" }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL));

        assertThat(findSavedMember().getEmail()).isEqualTo(EMAIL);
    }





    @Test
    @DisplayName("아무 필드도 보내지 않으면 400")
    void updateMyInfo_emptyRequest() throws Exception {
        updateMyInfo(sessionCookie, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value(startsWith("updateFieldPresent:")));
    }

    @Test
    @DisplayName("필드 이름을 잘못 보내면(nickName) 빈 요청으로 보고 400")
    void updateMyInfo_unknownFieldOnly() throws Exception {
        updateMyInfo(sessionCookie, """
                { "nickName": "용현2" }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(startsWith("updateFieldPresent:")));

        assertThat(findSavedMember().getNickname()).isEqualTo("용현");
    }

    @Test
    @DisplayName("닉네임이 공백뿐이면 400, 값은 바뀌지 않는다")
    void updateMyInfo_blankNickname() throws Exception {
        updateMyInfo(sessionCookie, """
                { "nickname": "   " }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(startsWith("nickname:")));

        assertThat(findSavedMember().getNickname()).isEqualTo("용현");
    }

    @Test
    @DisplayName("주소 묶음에 우편번호가 없으면 400 (주소 안쪽까지 검사)")
    void updateMyInfo_addressWithoutZipCode() throws Exception {
        updateMyInfo(sessionCookie, """
                { "address": { "address": "서울시 중구 세종대로 110" } }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(startsWith("address.zipCode:")));

        assertThat(findSavedMember().getAddress()).isEqualTo("서울시 강남구 테헤란로 123");
    }

    @Test
    @DisplayName("로그인 없이 수정하면 401")
    void updateMyInfo_withoutLogin() throws Exception {
        mockMvc.perform(patch("/api/members/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nickname": "용현2" }
                                """))
                .andExpect(status().isUnauthorized());
    }


    private Cookie loginAndGetCookie() throws Exception {
        String json = """
                { "email": "%s", "password": "%s" }
                """.formatted(EMAIL, PASSWORD);
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn().getResponse().getCookie("SESSION");
    }

    private ResultActions getMyInfo(Cookie cookie) throws Exception {
        return mockMvc.perform(get("/api/members/me").cookie(cookie));
    }

    private ResultActions updateMyInfo(Cookie cookie, String json) throws Exception {
        return mockMvc.perform(patch("/api/members/me")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private Member findSavedMember() {
        return memberRepository.findById(member.getId()).orElseThrow();
    }

}