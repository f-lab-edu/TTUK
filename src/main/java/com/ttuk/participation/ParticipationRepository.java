package com.ttuk.participation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface  ParticipationRepository extends JpaRepository<Participation, Long> {

    @Query("""
            select p.member.id
              from Participation p
             where p.groupPurchase.id = :groupPurchaseId
               and p.status = com.ttuk.participation.ParticipationStatus.JOINED
            """)
    List<Long> findJoinedMemberIds(@Param("groupPurchaseId") Long groupPurchaseId);

    @Query("""
            select case when count(p) > 0 then true else false end
              from Participation p
             where p.groupPurchase.id = :groupPurchaseId
               and p.member.id = :memberId
               and p.status = com.ttuk.participation.ParticipationStatus.JOINED
            """)
    boolean existsJoined(@Param("groupPurchaseId") Long groupPurchaseId,
                         @Param("memberId") Long memberId);

}