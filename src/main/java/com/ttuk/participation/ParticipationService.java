package com.ttuk.participation;

import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import com.ttuk.grouppurchase.GroupPurchase;
import com.ttuk.grouppurchase.GroupPurchaseRepository;
import com.ttuk.grouppurchase.event.GroupPurchaseCreatedEvent;
import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import com.ttuk.grouppurchase.redis.JoinOutcome;
import com.ttuk.grouppurchase.redis.JoinResult;
import com.ttuk.member.MemberRepository;
import com.ttuk.participation.dto.ParticipationCreateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
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
            case INVALID_HEADCOUNT -> throw new IllegalStateException("Redis 참여 인원 값이 올바르지 않습니다. groupPurchaseId=" + groupPurchaseId);
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

        List<Long> joinedMemberIds = participationRepository.findJoinedMemberIds(groupPurchaseId);
        groupPurchaseRedisRepository.restore(GroupPurchaseCreatedEvent.from(groupPurchase), joinedMemberIds);
        return groupPurchaseRedisRepository.join(groupPurchaseId, memberId);
    }

    // Redis Lua가 중복 참여를 먼저 막으므로, 저장 중 같은 회원의 JOINED가 이미 있다는 건
    // Redis 참여자에 빠져 있던 기존 참여자가 Lua를 통과한 경우다. 이때 Redis에 들어간 참여는
    // 오히려 DB와 맞는 상태라 되돌리지 않고, 사용자에게는 이미 참여 중이라고 응답한다.
    // 그 밖의 저장 실패는 실패한 요청이 자리를 차지하지 않도록 Redis 참여를 되돌린다.
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
            if (alreadyJoinedInDb(groupPurchaseId, memberId, e)) {
                log.warn("참여 저장: DB에 이미 JOINED 참여가 있음. Redis 참여자에 빠져 있던 회원으로 보고 Redis는 그대로 둠. "
                        + "groupPurchaseId={}, memberId={}", groupPurchaseId, memberId);
                throw new BusinessException(ErrorCode.ALREADY_JOINED);
            }
            rollbackRedis(groupPurchaseId, memberId, e);
            throw e;
        } catch (RuntimeException e) {
            rollbackRedis(groupPurchaseId, memberId, e);
            throw e;
        }
        return new ParticipationCreateResponse(result.headcount(), result.price());
    }

    private boolean alreadyJoinedInDb(Long groupPurchaseId, Long memberId, RuntimeException cause) {
        try {
            return participationRepository.existsJoined(groupPurchaseId, memberId);
        } catch (RuntimeException checkFailure) {
            cause.addSuppressed(checkFailure);
            return false;
        }
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