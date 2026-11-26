package com.ttuk.participation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface  ParticipationRepository extends JpaRepository<Participation, Long> {

    @Transactional
    @Modifying
    @Query("""
            update Participation p
               set p.status = com.ttuk.participation.ParticipationStatus.CANCELED,
                   p.canceledAt = :now,
                   p.updatedAt = :now
             where p.groupPurchase.id = :groupPurchaseId
               and p.member.id = :memberId
               and p.status = com.ttuk.participation.ParticipationStatus.JOINED
            """)
    int cancelJoined(@Param("groupPurchaseId") Long groupPurchaseId,
                     @Param("memberId") Long memberId,
                     @Param("now") LocalDateTime now);

    @Query("""
            select p.member.id
              from Participation p
             where p.groupPurchase.id = :groupPurchaseId
               and p.status = com.ttuk.participation.ParticipationStatus.JOINED
            """)
    List<Long> findJoinedMemberIds(@Param("groupPurchaseId") Long groupPurchaseId);

}