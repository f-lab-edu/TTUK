package com.ttuk.grouppurchase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupPurchaseSettlementScheduler {

    private static final Duration SETTLE_AFTER_CLOSE = Duration.ofMinutes(1);
    private static final int BATCH_SIZE = 100;

    private final GroupPurchaseRepository groupPurchaseRepository;
    private final GroupPurchaseSettlementService settlementService;
    private final Clock clock;

    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void settleDueGroupPurchases() {

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime threshold = now.minus(SETTLE_AFTER_CLOSE);
        int maxFailures = GroupPurchaseSettlementService.MAX_SETTLE_FAILURES;

        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(groupPurchaseRepository.findSettlementTargetIds(threshold, maxFailures, now, Limit.of(BATCH_SIZE)));
        ids.addAll(groupPurchaseRepository.findFilledSettlementTargetIds(maxFailures, now, Limit.of(BATCH_SIZE)));

        for (Long id : ids) {
            settlementService.settleSafely(id);
        }

    }

}