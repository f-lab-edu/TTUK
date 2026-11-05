package com.ttuk.category;

import com.ttuk.global.entity.BaseTimeEntity;
import com.ttuk.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_member_category",
        columnNames = {"member_id", "category_id"}),

       indexes = @Index(
                name = "idx_member_category_category",
               columnList = "category_id")
)
public class MemberCategory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", foreignKey = @ForeignKey(name = "fk_member_category_member"))
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", foreignKey = @ForeignKey(name = "fk_member_category_category"))
    private Category category;

    public MemberCategory(Member member, Category category) {
        this.member = member;
        this.category = category;
    }

}
