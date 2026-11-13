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

    @Builder
    private Participation(GroupPurchase groupPurchase, Member member, int quantity, LocalDateTime joinedAt) {
        this.groupPurchase = groupPurchase;
        this.member = member;
        this.quantity = quantity;
        this.joinedAt = joinedAt;
        this.status = ParticipationStatus.JOINED;
    }
}
