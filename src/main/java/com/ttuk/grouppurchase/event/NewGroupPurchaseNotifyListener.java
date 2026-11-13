package com.ttuk.grouppurchase.event;

import com.ttuk.notification.NotificationStreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewGroupPurchaseNotifyListener {

    private final NotificationStreamRepository notificationStreamRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifySubscribers(GroupPurchaseCreatedEvent event){

        try {
            notificationStreamRepository.addNewGroupPurchase(
                    event.groupPurchaseId(), event.categoryId(), event.title());
        } catch (RuntimeException e) {
            log.error("새 공동구매 알림 발행 실패 groupPurchaseId={}", event.groupPurchaseId(), e);
        }

    }

}
