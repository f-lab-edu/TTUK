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
    @Modifying(clearAutomatically = true, flushAutomatically = true)
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
               and g.settleFailCount < :maxFailures
               and (g.nextSettleAt is null or g.nextSettleAt <= :now)
             order by g.closeAt
            """)
    List<Long> findSettlementTargetIds(@Param("threshold") LocalDateTime threshold,
                                       @Param("maxFailures") int maxFailures,
                                       @Param("now") LocalDateTime now,
                                       Limit limit);

    @Query("""
            select g.id
              from GroupPurchase g
              join Participation p on p.groupPurchase = g
             where g.status = com.ttuk.grouppurchase.GroupPurchaseStatus.OPEN
               and g.maxHeadcount is not null
               and p.status = com.ttuk.participation.ParticipationStatus.JOINED
               and g.settleFailCount < :maxFailures
               and (g.nextSettleAt is null or g.nextSettleAt <= :now)
             group by g.id, g.maxHeadcount
            having count(p) >= g.maxHeadcount
             order by g.id
            """)
    List<Long> findFilledSettlementTargetIds(@Param("maxFailures") int maxFailures,
                                             @Param("now") LocalDateTime now,
                                             Limit limit);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update GroupPurchase g
               set g.settleFailCount = g.settleFailCount + 1,
                   g.nextSettleAt = :nextSettleAt,
                   g.updatedAt = :now
             where g.id = :groupPurchaseId
               and g.status = com.ttuk.grouppurchase.GroupPurchaseStatus.OPEN
            """)
    int recordSettleFailure(@Param("groupPurchaseId") Long groupPurchaseId,
                            @Param("nextSettleAt") LocalDateTime nextSettleAt,
                            @Param("now") LocalDateTime now);

}