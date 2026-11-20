package com.ttuk.grouppurchase;

import com.ttuk.global.config.ClockConfig;
import com.ttuk.global.response.PageResponse;
import com.ttuk.global.storage.S3ImageStorage;
import com.ttuk.grouppurchase.dto.*;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GroupPurchaseQueryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    @Mock
    GroupPurchaseQueryRepository queryRepository;
    @Mock
    GroupPurchaseRedisRepository redisRepository;
    @Mock
    S3ImageStorage imageStorage;

    GroupPurchaseQueryService queryService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ClockConfig.SERVICE_ZONE).toInstant(), ClockConfig.SERVICE_ZONE);
        queryService = new GroupPurchaseQueryService(queryRepository, redisRepository, imageStorage, clock);
    }

    @Test
    @DisplayName("Redis 조회가 실패해도 목록은 나오고, 인원은 0명·단가는 기본가로 채운다")
    void findOpenList_redisFailure() {
        GroupPurchaseListRequest request = new GroupPurchaseListRequest(null, null, null, null);
        GroupPurchaseListRow row = new GroupPurchaseListRow(
                12L, "대파 한 단", "신선식품", 3000, 50, NOW.plusDays(1), GroupPurchaseStatus.OPEN, null);
        given(queryRepository.findOpenList(request, NOW)).willReturn(List.of(row));
        given(queryRepository.countOpen(null, NOW)).willReturn(1L);
        given(redisRepository.findLiveValues(List.of(12L)))
                .willThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        PageResponse<GroupPurchaseSummaryResponse> response = queryService.findOpenList(request);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        GroupPurchaseSummaryResponse summary = response.content().get(0);
        assertThat(summary.currentHeadcount()).isZero();
        assertThat(summary.currentPrice()).isEqualTo(3000);
    }

    @Test
    @DisplayName("상세 Redis 조회가 실패해도 상세는 나오고, 0명·기본가·DB 상태·참여 안 함으로 채운다")
    void findDetail_redisFailure() {
        GroupPurchaseDetailRow row = new GroupPurchaseDetailRow(
                12L, "대파 한 단", "진도 대파", "단", "신선식품", "진도농장",
                3000, 2500, DiscountType.RATE, 5, 10, 50, NOW.plusDays(1), GroupPurchaseStatus.OPEN);
        given(queryRepository.findDetail(12L)).willReturn(Optional.of(row));
        given(queryRepository.findImageKeys(12L)).willReturn(List.of());
        given(redisRepository.findLiveDetail(12L, 7L))
                .willThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        GroupPurchaseDetailResponse response = queryService.findDetail(12L, 7L);

        assertThat(response.currentHeadcount()).isZero();
        assertThat(response.currentPrice()).isEqualTo(3000);
        assertThat(response.status()).isEqualTo(GroupPurchaseStatus.OPEN);
        assertThat(response.joined()).isFalse();
    }

}