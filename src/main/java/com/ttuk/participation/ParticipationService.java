package com.ttuk.participation;

import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import com.ttuk.grouppurchase.GroupPurchaseRepository;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import com.ttuk.grouppurchase.redis.JoinResult;
import com.ttuk.member.MemberRepository;
import com.ttuk.participation.dto.ParticipationCreateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

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

        return switch (result.outcome()) {
            case JOINED -> saveParticipation(groupPurchaseId, memberId, quantity, result);
            case NOT_OPEN -> throw new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_OPEN);
            case FULL -> throw new BusinessException(ErrorCode.GROUP_PURCHASE_FULL);
            case INVALID_HEADCOUNT -> throw new IllegalStateException("Redis 참여 인원 값이 올바르지 않습니다. groupPurchaseId=" + groupPurchaseId);
            case ALREADY_JOINED -> throw new BusinessException(ErrorCode.ALREADY_JOINED);
            case NOT_INITIALIZED -> throw notInitialized(groupPurchaseId);
        };
    }

    // Redis Lua가 중복 참여를 먼저 막으므로, 여기서 같은 회원의 JOINED가 이미 있다는 건
    // Redis 참여자에 빠져 있던 기존 참여자가 Lua를 통과한 경우다. 이때 Redis에 들어간 참여는
    // 오히려 DB와 맞는 상태라 되돌리지 않고, 사용자에게는 이미 참여 중이라고 응답한다.
    private ParticipationCreateResponse saveParticipation(Long groupPurchaseId, Long memberId,
                                                          int quantity, JoinResult result) {
        try {
            participationRepository.save(Participation.builder()
                    .groupPurchase(groupPurchaseRepository.getReferenceById(groupPurchaseId))
                    .member(memberRepository.getReferenceById(memberId))
                    .quantity(quantity)
                    .joinedAt(LocalDateTime.now(clock))
                    .build());
        } catch (DataIntegrityViolationException e) {
            if (participationRepository.existsJoined(groupPurchaseId, memberId)) {
                log.warn("참여 저장: DB에 이미 JOINED 참여가 있음. Redis 참여자에 빠져 있던 회원으로 보고 Redis는 그대로 둠. "
                        + "groupPurchaseId={}, memberId={}", groupPurchaseId, memberId);
                throw new BusinessException(ErrorCode.ALREADY_JOINED);
            }
            throw e;
        }

        return new ParticipationCreateResponse(result.headcount(), result.price());
    }

    private RuntimeException notInitialized(Long groupPurchaseId) {
        if (!groupPurchaseRepository.existsById(groupPurchaseId)) {
            return new BusinessException(ErrorCode.GROUP_PURCHASE_NOT_FOUND);
        }
        return new IllegalStateException("Redis가 초기화되지 않은 공동구매입니다. id=" + groupPurchaseId);
    }
}