package com.ttuk.category;

import com.ttuk.global.config.CacheConfig;
import com.ttuk.global.config.CacheVersion;
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
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public class CategoryIntegrationTest {

    private static final String EMAIL = "quill@ttuk.kr";
    private static final String PASSWORD = "a123456789";
    private static final String TEMP_NAME = "임시이름";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    CacheVersion cacheVersion;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(cacheKey());

        memberRepository.save(Member.builder()
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
        redisTemplate.delete(cacheKey());
        jdbcTemplate.update("UPDATE category SET name = ? WHERE name = ?", "신선식품", TEMP_NAME);
    }

    @Test
    @DisplayName("목록 조회 성공: 200, 초기 데이터 8개가 id 순서로 나온다")
    void getCategories_success() throws Exception {
        Cookie sessionCookie = loginAndGetCookie();

        getCategories(sessionCookie)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8))
                .andExpect(jsonPath("$.data[*].name").value(contains(
                        "신선식품", "가공식품", "생활용품", "전자제품",
                        "도서·문구", "뷰티", "유아동", "반려동물")));
    }

    @Test
    @DisplayName("로그인 없이 요청하면 401 UNAUTHORIZED")
    void getCategories_withoutLogin() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("첫 조회 후 Redis에 빌드 시각이 붙은 키로 1시간 이내 TTL과 함께 저장된다")
    void getCategories_storesCache() throws Exception {
        Cookie sessionCookie = loginAndGetCookie();
        assertThat(redisTemplate.hasKey(cacheKey())).isFalse();

        getCategories(sessionCookie).andExpect(status().isOk());

        assertThat(redisTemplate.hasKey(cacheKey())).isTrue();
        assertThat(redisTemplate.hasKey(CacheConfig.CATEGORIES + "::all")).isFalse();
        assertThat(redisTemplate.getExpire(cacheKey())).isPositive().isLessThanOrEqualTo(3600L);
    }

    @Test
    @DisplayName("캐시가 있으면 DB가 바뀌어도 캐시 값을 쓰고, 캐시를 지우면 DB의 새 값을 읽는다")
    void getCategories_usesCache() throws Exception {
        Cookie sessionCookie = loginAndGetCookie();

        getCategories(sessionCookie)
                .andExpect(jsonPath("$.data[0].name").value("신선식품"));

        jdbcTemplate.update("UPDATE category SET name = ? WHERE name = ?", TEMP_NAME, "신선식품");

        getCategories(sessionCookie)
                .andExpect(jsonPath("$.data[0].name").value("신선식품"));

        redisTemplate.delete(cacheKey());

        getCategories(sessionCookie)
                .andExpect(jsonPath("$.data[0].name").value(TEMP_NAME));
    }

    private String cacheKey() {
        return cacheVersion.prefix() + CacheConfig.CATEGORIES + "::all";
    }

    private ResultActions getCategories(Cookie sessionCookie) throws Exception {
        return mockMvc.perform(get("/api/categories").cookie(sessionCookie));
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
}