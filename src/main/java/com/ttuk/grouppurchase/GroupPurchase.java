package com.ttuk.grouppurchase;

import com.ttuk.category.Category;
import com.ttuk.global.entity.BaseTimeEntity;
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
@Table(indexes = {
        @Index(name = "idx_group_purchase_status_category_close", columnList = "status, category_id, close_at"),
        @Index(name = "idx_group_purchase_status_close", columnList = "status, close_at")
})
public class GroupPurchase extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", foreignKey = @ForeignKey(name = "fk_group_purchase_seller"))
    private Member seller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", foreignKey = @ForeignKey(name = "fk_group_purchase_category"))
    private Category category;

    @Column(nullable = false, length = 50)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false)
    private int basePrice;

    @Column(nullable = false)
    private int floorPrice;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DiscountType discountType;

    @Column(nullable = false)
    private int discountValue;

    @Column(nullable = false)
    private int discountStepSize;

    private Integer maxHeadcount;

    @Column(nullable = false)
    private LocalDateTime closeAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private GroupPurchaseStatus status;

    private Integer finalHeadcount;

    private Integer finalPrice;

    @Column(length = 200)
    private String cancelReason;

    private LocalDateTime closedAt;

    private LocalDateTime canceledAt;

    @Builder
    private GroupPurchase(Member seller, Category category, String title, String description,
                          String unit, int basePrice, int floorPrice,
                          DiscountType discountType, int discountValue, int discountStepSize,
                          Integer maxHeadcount, LocalDateTime closeAt) {
        this.seller = seller;
        this.category = category;
        this.title = title;
        this.description = description;
        this.unit = unit;
        this.basePrice = basePrice;
        this.floorPrice = floorPrice;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.discountStepSize = discountStepSize;
        this.maxHeadcount = maxHeadcount;
        this.closeAt = closeAt;
        this.status = GroupPurchaseStatus.OPEN;
    }

    public boolean isJoinableAt(LocalDateTime now){
        return status == GroupPurchaseStatus.OPEN && now.isBefore(closeAt);
    }
}