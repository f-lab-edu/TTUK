package com.ttuk.grouppurchase;

import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import com.ttuk.grouppurchase.redis.SettlementSnapshot;
import com.ttuk.participation.ParticipationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupPurchaseSettlementService {

    public static final int MAX_SETTLE_FAILURES = 5;
    private static final Duration SETTLE_RETRY_BASE = Duration.ofMinutes(1);

    private final GroupPurchaseRepository groupPurchaseRepository;
    private final ParticipationRepository participationRepository;
    private final GroupPurchaseRedisRepository groupPurchaseRedisRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    // 정원을 채운 참여 직후에 부른다. DB의 JOINED 수가 정원보다 적으면
    // 다른 참여자의 저장이 아직 끝나지 않은 것이므로 정산하지 않고 스케줄러에 맡긴다.
    public void settleIfFilled(Long groupPurchaseId) {
        try {
            if (isFilledInDb(groupPurchaseId)) {
                settle(groupPurchaseId);
            }
        } catch (RuntimeException e) {
            handleFailure(groupPurchaseId, e);
        }
    }

    public void settleSafely(Long groupPurchaseId) {
        try {
            settle(groupPurchaseId);
        } catch (RuntimeException e) {
            handleFailure(groupPurchaseId, e);
        }
    }

    public void settle(Long groupPurchaseId) {
        Optional<GroupPurchase> found = groupPurchaseRepository.findById(groupPurchaseId);
        if (found.isEmpty() || found.get().getStatus() != GroupPurchaseStatus.OPEN) {
            return;
        }
        GroupPurchase groupPurchase = found.get();
        LocalDateTime now = LocalDateTime.now(clock);

        Optional<SettlementSnapshot> snapshot =
                groupPurchaseRedisRepository.takeSettlementSnapshot(groupPurchaseId);
        if (snapshot.isEmpty()) {
            settleWithoutRedis(groupPurchase, now);
            return;
        }
        settleWithRedis(groupPurchaseId, snapshot.get(), now);
    }

    private boolean isFilledInDb(Long groupPurchaseId) {
        Optional<GroupPurchase> found = groupPurchaseRepository.findById(groupPurchaseId);
        if (found.isEmpty() || found.get().getMaxHeadcount() == null) {
            return false;
        }
        long joined = participationRepository.countJoined(groupPurchaseId);
        if (joined < found.get().getMaxHeadcount()) {
            log.info("정원 참 정산 보류: 저장이 끝나지 않은 참여가 있어 스케줄러가 정산함. groupPurchaseId={}, joined={}",
                    groupPurchaseId, joined);
            return false;
        }
        return true;
    }

    private void handleFailure(Long groupPurchaseId, RuntimeException cause) {
        log.error("정산 실패. groupPurchaseId={}", groupPurchaseId, cause);
        try {
            recordFailure(groupPurchaseId);
        } catch (RuntimeException recordFailure) {
            log.error("정산 실패 기록 실패. groupPurchaseId={}", groupPurchaseId, recordFailure);
        }
    }

    private void recordFailure(Long groupPurchaseId) {
        Optional<GroupPurchase> found = groupPurchaseRepository.findById(groupPurchaseId);
        if (found.isEmpty()) {
            return;
        }
        int failCount = found.get().getSettleFailCount();
        LocalDateTime now = LocalDateTime.now(clock);
        long multiplier = 1L << Math.min(failCount, MAX_SETTLE_FAILURES);
        LocalDateTime nextSettleAt = now.plus(SETTLE_RETRY_BASE.multipliedBy(multiplier));

        if (groupPurchaseRepository.recordSettleFailure(groupPurchaseId, nextSettleAt, now) == 1
                && failCount + 1 >= MAX_SETTLE_FAILURES) {
            log.error("정산 실패 한도 도달: 더 이상 자동으로 정산하지 않음. 직접 확인 필요. groupPurchaseId={}",
                    groupPurchaseId);
        }
    }

    private void settleWithRedis(Long groupPurchaseId, SettlementSnapshot snapshot, LocalDateTime now) {
        if (!snapshot.settleable()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            if (groupPurchaseRepository.closeOpen(
                    groupPurchaseId, snapshot.headcount(), snapshot.price(), now) == 0) {
                return;
            }
            reconcile(groupPurchaseId, snapshot.participantIds(), now);
        });
    }

    private void reconcile(Long groupPurchaseId, Set<Long> redisParticipantIds, LocalDateTime now) {
        List<Long> joinedMemberIds = participationRepository.findJoinedMemberIds(groupPurchaseId);

        List<Long> dbOnly = new ArrayList<>();
        for (Long memberId : joinedMemberIds) {
            if (!redisParticipantIds.contains(memberId)) {
                dbOnly.add(memberId);
            }
        }
        if (!dbOnly.isEmpty()) {
            int canceled = participationRepository.cancelJoinedMembers(groupPurchaseId, dbOnly, now);
            log.warn("정산 대조: Redis에 없는 DB 참여를 CANCELED로 정리함. groupPurchaseId={}, memberIds={}, canceled={}",
                    groupPurchaseId, dbOnly, canceled);
        }

        Set<Long> joined = new HashSet<>(joinedMemberIds);
        List<Long> redisOnly = new ArrayList<>();
        for (Long memberId : redisParticipantIds) {
            if (!joined.contains(memberId)) {
                redisOnly.add(memberId);
            }
        }
        if (!redisOnly.isEmpty()) {
            log.error("정산 대조: DB에 참여 기록이 없는 Redis 참여자가 있음. groupPurchaseId={}, memberIds={}",
                    groupPurchaseId, redisOnly);
        }
    }

    private void settleWithoutRedis(GroupPurchase groupPurchase, LocalDateTime now) {
        if (now.isBefore(groupPurchase.getCloseAt())) {
            return;
        }
        Long groupPurchaseId = groupPurchase.getId();
        transactionTemplate.executeWithoutResult(status -> {
            int headcount = participationRepository.findJoinedMemberIds(groupPurchaseId).size();
            int price = groupPurchase.discountPolicy().priceAt(headcount);
            if (groupPurchaseRepository.closeOpen(groupPurchaseId, headcount, price, now) == 1) {
                log.warn("정산: Redis 키가 없어 DB 기준으로 마감함. groupPurchaseId={}, headcount={}, price={}",
                        groupPurchaseId, headcount, price);
            }
        });
    }
}