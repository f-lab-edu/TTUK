package com.ttuk.participation;

import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import com.ttuk.grouppurchase.GroupPurchase;
import com.ttuk.grouppurchase.GroupPurchaseRepository;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import com.ttuk.grouppurchase.redis.JoinOutcome;
import com.ttuk.grouppurchase.redis.JoinResult;
import com.ttuk.member.MemberRepository;
import com.ttuk.participation.dto.ParticipationCreateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
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
        if (result.outcome() == JoinOutcome.NOT_INITIALIZED) {
            result = initializeAndRetry(groupPurchaseId, memberId);
        }

        return switch (result.outcome()) {
            case JOINED -> saveParticipation(groupPurchaseId, memberId, quantity, result);
            case NOT_OPEN -> throw new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_OPEN);
            case FULL -> throw new BusinessException(ErrorCode.GROUP_PURCHASE_FULL);
            case ALREADY_JOINED -> throw new BusinessException(ErrorCode.ALREADY_JOINED);
            case NOT_INITIALIZED -> throw new IllegalStateException(
                    "초기화 후에도 Redis 키가 없습니다. groupPurchaseId=" + groupPurchaseId);
        };
    }

    private JoinResult initializeAndRetry(Long groupPurchaseId, Long memberId) {
        Optional<GroupPurchase> found = groupPurchaseRepository.findById(groupPurchaseId);
        if (found.isEmpty()) {
            throw new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_FOUND);
        }
        GroupPurchase groupPurchase = found.get();
        if (!groupPurchase.isJoinableAt(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_OPEN);
        }

        groupPurchaseRedisRepository.initialize(groupPurchase);
        return groupPurchaseRedisRepository.join(groupPurchaseId, memberId);
    }

    private ParticipationCreateResponse saveParticipation(Long groupPurchaseId, Long memberId,
                                                          int quantity, JoinResult result) {
        try {
            participationRepository.save(Participation.builder()
                    .groupPurchase(groupPurchaseRepository.getReferenceById(groupPurchaseId))
                    .member(memberRepository.getReferenceById(memberId))
                    .quantity(quantity)
                    .joinedAt(LocalDateTime.now(clock))
                    .build());
        } catch (RuntimeException e) {
            rollbackRedis(groupPurchaseId, memberId, e);
            throw e;
        }
        return new ParticipationCreateResponse(result.headcount(), result.price());
    }

    private void rollbackRedis(Long groupPurchaseId, Long memberId, RuntimeException cause) {
        try {
            if (!groupPurchaseRedisRepository.rollbackJoin(groupPurchaseId, memberId)) {
                log.warn("참여 보상: 되돌릴 Redis 참여가 없음. groupPurchaseId={}, memberId={}",
                        groupPurchaseId, memberId);
            }
        } catch (RuntimeException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
            log.error("참여 보상 실패: Redis와 DB가 어긋남. groupPurchaseId={}, memberId={}",
                    groupPurchaseId, memberId, rollbackFailure);
        }
    }
}