package com.ttuk.grouppurchase;

import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import com.ttuk.grouppurchase.redis.SettlementSnapshot;
import com.ttuk.participation.ParticipationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
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

    private final GroupPurchaseRepository groupPurchaseRepository;
    private final ParticipationRepository participationRepository;
    private final GroupPurchaseRedisRepository groupPurchaseRedisRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public void settle(Long groupPurchaseId) {
        Optional<GroupPurchase> found = groupPurchaseRepository.findById(groupPurchaseId);
        if (found.isEmpty() || found.get().getStatus() != GroupPurchaseStatus.OPEN) {
            return;
        }
        GroupPurchase groupPurchase = found.get();
        LocalDateTime now = LocalDateTime.now(clock);

        Optional<SettlementSnapshot> snapshot =
                groupPurchaseRedisRepository.findSettlementSnapshot(groupPurchaseId);
        if (snapshot.isEmpty()) {
            settleWithoutRedis(groupPurchase, now);
            return;
        }
        settleWithRedis(groupPurchaseId, snapshot.get(), now);
    }

    private void settleWithRedis(Long groupPurchaseId, SettlementSnapshot snapshot, LocalDateTime now) {
        if (snapshot.open()) {
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