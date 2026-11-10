package com.ttuk.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemberCategoryRepository extends JpaRepository<MemberCategory, Long> {

    boolean existsByMemberIdAndCategoryId(Long memberId, Long categoryId);

    @Query("select mc.category.id from MemberCategory mc where mc.member.id = :memberId")
    List<Long> findCategoryIdsByMemberId(@Param("memberId") Long memberId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MemberCategory mc where mc.member.id = :memberId and mc.category.id = :categoryId")
    int deleteByMemberIdAndCategoryId(@Param("memberId") Long memberId, @Param("categoryId") Long categoryId);
}