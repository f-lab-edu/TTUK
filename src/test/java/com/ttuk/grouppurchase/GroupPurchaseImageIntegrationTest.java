package com.ttuk.grouppurchase;

import com.jayway.jsonpath.JsonPath;
import com.ttuk.global.config.S3Properties;
import com.ttuk.member.Member;
import com.ttuk.member.MemberRepository;
import com.ttuk.member.MemberRole;
import com.ttuk.notification.NotificationStreamRepository;
import com.ttuk.support.TestMultipartFiles;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public class GroupPurchaseImageIntegrationTest {

    private static final String SELLER_EMAIL = "seller@ttuk.kr";
    private static final String PASSWORD = "a123456789";
    private static final long FRESH_FOOD_ID = 1L;   // data.sql 첫 번째: 신선식품
    private static final String IMAGE_PREFIX = "images/";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    GroupPurchaseRepository groupPurchaseRepository;
    @MockitoSpyBean
    GroupPurchaseImageRepository groupPurchaseImageRepository;
    @Autowired
    S3Client s3Client;
    @Autowired
    S3Properties s3Properties;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        deleteAllStoredImages();
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
        deleteAllStoredImages();
        redisTemplate.delete(NotificationStreamRepository.STREAM_KEY);
        Set<String> keys = redisTemplate.keys("group_purchase:*");
        redisTemplate.delete(keys);
        groupPurchaseImageRepository.deleteAll();
        groupPurchaseRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("이미지 2장 등록: 순서대로 저장되고 S3에 업로드되며, 첫 이미지가 썸네일 URL이 된다")
    void create_withImages_storesInOrderAndUploads() throws Exception {
        Cookie sellerCookie = loginAndGetCookie(SELLER_EMAIL);

        String body = mockMvc.perform(multipart("/api/group-purchases")
                        .file(TestMultipartFiles.requestPart(json()))
                        .file(TestMultipartFiles.pngImage("first.png"))
                        .file(TestMultipartFiles.pngImage("second.png"))
                        .cookie(sellerCookie))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        List<GroupPurchaseImage> images = groupPurchaseImageRepository.findAll(Sort.by("sortOrder"));
        assertThat(images).hasSize(2);
        assertThat(images.get(0).getSortOrder()).isZero();
        assertThat(images.get(0).isThumbnail()).isTrue();
        assertThat(images.get(1).getSortOrder()).isEqualTo(1);
        assertThat(images.get(0).getImageKey()).startsWith(IMAGE_PREFIX).endsWith(".png");

        String thumbnailUrl = JsonPath.read(body, "$.data.thumbnailUrl");
        assertThat(thumbnailUrl).isEqualTo(s3Properties.publicBaseUrl() + "/" + images.get(0).getImageKey());

        assertThat(storedImages()).hasSize(2);
        HeadObjectResponse stored = s3Client.headObject(builder -> builder
                .bucket(s3Properties.bucket())
                .key(images.get(0).getImageKey()));
        assertThat(stored.contentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
        assertThat(stored.cacheControl()).contains("immutable");
    }

    @Test
    @DisplayName("이름·Content-Type만 PNG인 위조 파일이면 400 INVALID_IMAGE, DB와 S3 모두 비어 있다")
    void create_fakeImage_rejected() throws Exception {
        Cookie sellerCookie = loginAndGetCookie(SELLER_EMAIL);

        mockMvc.perform(multipart("/api/group-purchases")
                        .file(TestMultipartFiles.requestPart(json()))
                        .file(TestMultipartFiles.fakePngImage("virus.png"))
                        .cookie(sellerCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_IMAGE"));

        assertThat(groupPurchaseRepository.count()).isZero();
        assertThat(storedImages()).isEmpty();
    }

    @Test
    @DisplayName("S3 업로드 후 DB 저장이 실패하면 트랜잭션이 롤백되고, 올린 이미지도 S3에서 삭제된다")
    void create_dbFailsAfterUpload_compensatesS3() throws Exception {
        doThrow(new IllegalStateException("DB 저장 실패 흉내"))
                .when(groupPurchaseImageRepository).saveAll(anyIterable());
        Cookie sellerCookie = loginAndGetCookie(SELLER_EMAIL);

        mockMvc.perform(multipart("/api/group-purchases")
                        .file(TestMultipartFiles.requestPart(json()))
                        .file(TestMultipartFiles.pngImage("first.png"))
                        .file(TestMultipartFiles.pngImage("second.png"))
                        .cookie(sellerCookie))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));

        assertThat(groupPurchaseRepository.count()).isZero();
        assertThat(storedImages()).isEmpty();
    }

    private List<S3Object> storedImages() {
        return s3Client.listObjectsV2(builder -> builder
                .bucket(s3Properties.bucket())
                .prefix(IMAGE_PREFIX)).contents();
    }

    private void deleteAllStoredImages() {
        for (S3Object object : storedImages()) {
            s3Client.deleteObject(builder -> builder.bucket(s3Properties.bucket()).key(object.key()));
        }
    }

    private String json() {
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
                """.formatted(FRESH_FOOD_ID, LocalDateTime.now().plusDays(3).withNano(0));
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