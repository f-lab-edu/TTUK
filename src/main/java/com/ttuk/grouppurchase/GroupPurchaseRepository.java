package com.ttuk.grouppurchase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface GroupPurchaseRepository extends JpaRepository<GroupPurchase, Long> {

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query("""
             update GroupPurchase g
                           set g.status = com.ttuk.grouppurchase.GroupPurchaseStatus.CLOSED,
                               g.finalHeadcount = :finalHeadcount,
                               g.finalPrice = :finalPrice,
                               g.closedAt = :now,
                               g.updatedAt = :now
                         where g.id = :groupPurchaseId
                           and g.status = com.ttuk.grouppurchase.GroupPurchaseStatus.OPEN
            """)
    int closeOpen(@Param("groupPurchaseId") Long groupPurchaseId,
                  @Param("finalHeadcount") int finalHeadcount,
                  @Param("finalPrice") int finalPrice,
                  @Param("now") LocalDateTime now);


}
