package com.ttuk.grouppurchase;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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


    @Query("""
            select g.id
              from GroupPurchase g
             where g.status = com.ttuk.grouppurchase.GroupPurchaseStatus.OPEN
               and g.closeAt <= :threshold
             order by g.closeAt
            """)
    List<Long> findSettlementTargetIds(@Param("threshold") LocalDateTime threshold, Limit limit);

    @Transactional
    @Modifying
    @Query("""
            update GroupPurchase g
               set g.status = com.ttuk.grouppurchase.GroupPurchaseStatus.CANCELED,
                   g.cancelReason = :reason,
                   g.canceledAt = :now,
                   g.finalHeadcount = :finalHeadcount,
                   g.finalPrice = :finalPrice,
                   g.updatedAt = :now
             where g.id = :groupPurchaseId
               and g.status = com.ttuk.grouppurchase.GroupPurchaseStatus.OPEN
            """)
    int cancelOpen(@Param("groupPurchaseId") Long groupPurchaseId,
                   @Param("reason") String reason,
                   @Param("finalHeadcount") int finalHeadcount,
                   @Param("finalPrice") int finalPrice,
                   @Param("now") LocalDateTime now);

}
