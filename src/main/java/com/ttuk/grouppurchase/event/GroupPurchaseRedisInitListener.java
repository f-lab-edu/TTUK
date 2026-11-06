package com.ttuk.grouppurchase.event;

import com.ttuk.grouppurchase.redis.GroupPurchaseRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupPurchaseRedisInitListener {

    private final GroupPurchaseRedisRepository groupPurchaseRedisRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void initialize(GroupPurchaseCreatedEvent event){

        try {

            boolean created = groupPurchaseRedisRepository.initialize(event);
            if (!created) {
                log.warn("이미 초기화된 공동구매 Redis 키 groupPurchaseId={}", event.groupPurchaseId());
            }

        } catch (RuntimeException e) {

            log.error("공동구매 Redis 키 초기화 실패 groupPurchaseId={}", event.groupPurchaseId(), e);

        }

    }


}
