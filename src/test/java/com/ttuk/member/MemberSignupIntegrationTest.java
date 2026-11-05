package com.ttuk.member;

import com.ttuk.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class MemberSignupIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("회원가입 성공: 201과 memberId를 돌려주고, 비밀번호는 해시·연락처는 숫자만 저장된다")
    void signup_success() throws Exception {
        signup(validJson("quill@ttuk.kr"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberId").isNumber())
                .andExpect(jsonPath("$.error").doesNotExist());

        Member saved = memberRepository.findAll().get(0);
        assertThat(saved.getPassword()).isNotEqualTo("a123456789");
        assertThat(passwordEncoder.matches("a123456789", saved.getPassword())).isTrue();
        assertThat(saved.getPhoneNum()).isEqualTo("01012345678");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 409 DUPLICATE_EMAIL")
    void signup_duplicateEmail() throws Exception {
        signup(validJson("quillkim@ttuk.com")).andExpect(status().isCreated());

        signup(validJson("quillkim@ttuk.com")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("이메일 형식이 틀리면 400 INVALID_INPUT, message에 필드 이름이 들어간다")
    void signup_invalidEmail() throws Exception {
        signup(validJson("quill"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.startsWith("email:")));

        assertThat(memberRepository.count()).isZero();
    }

    @Test
    @DisplayName("role에 없는 값이 오면 400 INVALID_INPUT")
    void signup_unknownRole() throws Exception {
        String json = validJson("quill@ttuk.kr").replace("\"BUYER\"", "\"ADMIN\"");

        signup(json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("상세 주소는 없어도 가입된다")
    void signup_withoutAddressDetail() throws Exception {
        String json = """
                {
                  "email": "quill@ttuk.kr",
                  "password": "a123456789",
                  "nickname": "용현",
                  "role": "BUYER",
                  "phoneNum": "010-1234-5678",
                  "zipCode": "06236",
                  "address": "서울시 강남구 테헤란로 123"
                }
                """;

        signup(json).andExpect(status().isCreated());

        assertThat(memberRepository.findAll().get(0).getAddressDetail()).isNull();
    }



    private ResultActions signup(String json) throws Exception {
        return mockMvc.perform(post("/api/members")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String validJson(String email){
        return """
                {
                  "email": "%s",
                  "password": "a123456789",
                  "nickname": "용현",
                  "role": "BUYER",
                  "phoneNum": "010-1234-5678",
                  "zipCode": "06236",
                  "address": "서울시 강남구 테헤란로 123",
                  "addressDetail": "4층 401호"
                }
                """.formatted(email);
    }


}
