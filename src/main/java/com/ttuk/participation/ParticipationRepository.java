package com.ttuk.participation;

import com.ttuk.participation.dto.JoinedQuantity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface  ParticipationRepository extends JpaRepository<Participation, Long> {

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
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

    @Query("""
            select count(p)
              from Participation p
             where p.groupPurchase.id = :groupPurchaseId
               and p.status = com.ttuk.participation.ParticipationStatus.JOINED
            """)
    long countJoined(@Param("groupPurchaseId") Long groupPurchaseId);

    @Query("""
            select case when count(p) > 0 then true else false end
              from Participation p
             where p.groupPurchase.id = :groupPurchaseId
               and p.member.id = :memberId
               and p.status = com.ttuk.participation.ParticipationStatus.JOINED
            """)
    boolean existsJoined(@Param("groupPurchaseId") Long groupPurchaseId,
                         @Param("memberId") Long memberId);

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Participation p
               set p.status = com.ttuk.participation.ParticipationStatus.CANCELED,
                   p.canceledAt = :now,
                   p.updatedAt = :now
             where p.groupPurchase.id = :groupPurchaseId
               and p.member.id in :memberIds
               and p.status = com.ttuk.participation.ParticipationStatus.JOINED
            """)
    int cancelJoinedMembers(@Param("groupPurchaseId") Long groupPurchaseId,
                            @Param("memberIds") Collection<Long> memberIds,
                            @Param("now") LocalDateTime now);

    @Query("""
            select new com.ttuk.participation.dto.JoinedQuantity(p.groupPurchase.id, sum(p.quantity))
              from Participation p
             where p.groupPurchase.id in :groupPurchaseIds
               and p.status = com.ttuk.participation.ParticipationStatus.JOINED
             group by p.groupPurchase.id
            """)
    List<JoinedQuantity> sumJoinedQuantities(@Param("groupPurchaseIds") Collection<Long> groupPurchaseIds);

}