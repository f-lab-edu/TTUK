package com.ttuk.grouppurchase;

import com.ttuk.category.CategoryRepository;
import com.ttuk.global.config.ClockConfig;
import com.ttuk.global.error.BusinessException;
import com.ttuk.global.storage.S3ImageStorage;
import com.ttuk.grouppurchase.dto.GroupPurchaseCancelRequest;
import com.ttuk.grouppurchase.redis.GroupPurchaseCancelOutcome;
import com.ttuk.grouppurchase.redis.GroupPurchaseCancelResult;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import com.ttuk.member.MemberRepository;
import com.ttuk.participation.ParticipationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class GroupPurchaseServiceCancelTest {

    private static final Long SELLER_ID = 1L;
    private static final Long GROUP_PURCHASE_ID = 7L;
    private static final String REASON = "산지 기상 악화로 물량 확보가 어렵습니다";

    private final GroupPurchaseRepository groupPurchaseRepository = mock(GroupPurchaseRepository.class);
    private final GroupPurchaseRedisRepository groupPurchaseRedisRepository = mock(GroupPurchaseRedisRepository.class);
    private GroupPurchaseService groupPurchaseService;

    @BeforeEach
    void setUp() {
        groupPurchaseService = new GroupPurchaseService(
                groupPurchaseRepository,
                mock(GroupPurchaseImageRepository.class),
                mock(CategoryRepository.class),
                mock(MemberRepository.class),
                mock(GroupPurchaseImageValidator.class),
                mock(S3ImageStorage.class),
                mock(TransactionTemplate.class),
                mock(ApplicationEventPublisher.class),
                Clock.fixed(Instant.parse("2026-11-26T03:00:00Z"), ClockConfig.SERVICE_ZONE),
                groupPurchaseRedisRepository,
                mock(ParticipationRepository.class));

        GroupPurchase groupPurchase = mock(GroupPurchase.class);
        given(groupPurchase.isSoldBy(SELLER_ID)).willReturn(true);
        given(groupPurchase.getStatus()).willReturn(GroupPurchaseStatus.OPEN);
        given(groupPurchaseRepository.findById(GROUP_PURCHASE_ID)).willReturn(Optional.of(groupPurchase));
        given(groupPurchaseRedisRepository.cancelGroupPurchase(GROUP_PURCHASE_ID))
                .willReturn(new GroupPurchaseCancelResult(GroupPurchaseCancelOutcome.CANCELED, 2, 2850, 2));
    }

    @Test
    @DisplayName("Redis를 CANCELED로 바꾼 뒤 DB UPDATE가 0행이면, 다른 요청이 먼저 마무리한 것이라 Redis를 되돌리지 않고 409다")
    void cancel_dbAlreadyNotOpen_doesNotReopen() {
        given(groupPurchaseRepository.cancelOpen(eq(GROUP_PURCHASE_ID), anyString(), anyInt(), anyInt(), any()))
                .willReturn(0);

        assertThatThrownBy(() -> groupPurchaseService.cancel(SELLER_ID, GROUP_PURCHASE_ID, new GroupPurchaseCancelRequest(REASON)))
                .isInstanceOf(BusinessException.class);

        then(groupPurchaseRedisRepository).should(never()).reopenGroupPurchase(anyLong());
    }

    @Test
    @DisplayName("DB UPDATE가 예외로 실패하면 DB는 OPEN으로 남으므로 Redis를 OPEN으로 되돌린다")
    void cancel_dbFailure_reopens() {
        given(groupPurchaseRepository.cancelOpen(eq(GROUP_PURCHASE_ID), anyString(), anyInt(), anyInt(), any()))
                .willThrow(new IllegalStateException("DB 실패"));
        given(groupPurchaseRedisRepository.reopenGroupPurchase(GROUP_PURCHASE_ID)).willReturn(true);

        assertThatThrownBy(() -> groupPurchaseService.cancel(SELLER_ID, GROUP_PURCHASE_ID, new GroupPurchaseCancelRequest(REASON)))
                .isInstanceOf(IllegalStateException.class);

        then(groupPurchaseRedisRepository).should().reopenGroupPurchase(GROUP_PURCHASE_ID);
    }
}