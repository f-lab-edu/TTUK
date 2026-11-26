package com.ttuk.participation;

import com.ttuk.global.entity.BaseTimeEntity;
import com.ttuk.grouppurchase.GroupPurchase;
import com.ttuk.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_participation_joined_member",
        columnNames = {"group_purchase_id", "joined_member_id"}))
public class Participation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_purchase_id",
            foreignKey = @ForeignKey(name = "fk_participation_group_purchase"))
    private GroupPurchase groupPurchase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id",
            foreignKey = @ForeignKey(name = "fk_participation_member"))
    private Member member;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ParticipationStatus status;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    private LocalDateTime canceledAt;

    // JOINED일 때만 member_id, 그 외에는 NULL인 DB 계산 컬럼.
    // (group_purchase_id, joined_member_id) 유니크로 같은 회원의 JOINED를 하나로 제한하고,
    // NULL은 서로 중복으로 보지 않아 취소 기록은 여러 개 남길 수 있다.
    @Column(name = "joined_member_id",
            columnDefinition = "bigint generated always as (case when status = 'JOINED' then member_id end) virtual",
            insertable = false, updatable = false)
    private Long joinedMemberId;

    @Builder
    private Participation(GroupPurchase groupPurchase, Member member, int quantity, LocalDateTime joinedAt) {
        this.groupPurchase = groupPurchase;
        this.member = member;
        this.quantity = quantity;
        this.joinedAt = joinedAt;
        this.status = ParticipationStatus.JOINED;
    }
}