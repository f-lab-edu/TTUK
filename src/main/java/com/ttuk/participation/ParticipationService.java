package com.ttuk.participation;

import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import com.ttuk.grouppurchase.GroupPurchaseRepository;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import com.ttuk.grouppurchase.redis.JoinResult;
import com.ttuk.member.MemberRepository;
import com.ttuk.participation.dto.ParticipationCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ParticipationService {

    private final GroupPurchaseRedisRepository groupPurchaseRedisRepository;
    private final GroupPurchaseRepository groupPurchaseRepository;
    private final MemberRepository memberRepository;
    private final ParticipationRepository participationRepository;
    private final Clock clock;

    public ParticipationCreateResponse join(Long groupPurchaseId, Long memberId, int quantity) {
        JoinResult result = groupPurchaseRedisRepository.join(groupPurchaseId, memberId);

        return switch (result.outcome()) {
            case JOINED -> saveParticipation(groupPurchaseId, memberId, quantity, result);
            case NOT_OPEN -> throw new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_OPEN);
            case FULL -> throw new BusinessException(ErrorCode.GROUP_PURCHASE_FULL);
            case ALREADY_JOINED -> throw new BusinessException(ErrorCode.ALREADY_JOINED);
            case NOT_INITIALIZED -> throw notInitialized(groupPurchaseId);
        };
    }

    private ParticipationCreateResponse saveParticipation(Long groupPurchaseId, Long memberId,
                                                          int quantity, JoinResult result) {
        participationRepository.save(Participation.builder()
                .groupPurchase(groupPurchaseRepository.getReferenceById(groupPurchaseId))
                .member(memberRepository.getReferenceById(memberId))
                .quantity(quantity)
                .joinedAt(LocalDateTime.now(clock))
                .build());

        return new ParticipationCreateResponse(result.headcount(), result.price());
    }

    private RuntimeException notInitialized(Long groupPurchaseId) {
        if (!groupPurchaseRepository.existsById(groupPurchaseId)) {
            return new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_FOUND);
        }
        return new IllegalStateException("Redis가 초기화되지 않은 공동구매입니다. id=" + groupPurchaseId);
    }
}