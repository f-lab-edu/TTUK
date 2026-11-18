package com.ttuk.grouppurchase.redis;

import com.ttuk.grouppurchase.GroupPurchaseStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record SettlementSnapshot(
        boolean open,
        int headcount,
        int price,
        Set<Long> participantIds
) {
    public static SettlementSnapshot from(Long groupPurchaseId, List<String> scriptResult) {
        String headcount = scriptResult.get(1);
        String price = scriptResult.get(2);
        if (headcount.isEmpty() || price.isEmpty()) {
            throw new IllegalStateException(
                    "정산할 Redis 인원·단가 값이 없습니다. groupPurchaseId=" + groupPurchaseId);
        }
        Set<Long> participantIds = new HashSet<>();
        for (String memberId : scriptResult.subList(3, scriptResult.size())) {
            participantIds.add(Long.valueOf(memberId));
        }
        return new SettlementSnapshot(
                GroupPurchaseStatus.OPEN.name().equals(scriptResult.get(0)),
                Integer.parseInt(headcount),
                Integer.parseInt(price),
                Set.copyOf(participantIds));
    }
}