package com.ttuk.grouppurchase;

import com.ttuk.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_group_purchase_image_order",
        columnNames = {"group_purchase_id", "sort_order"}))
public class GroupPurchaseImage extends BaseTimeEntity {

    private static final int THUMBNAIL_ORDER = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_purchase_id",
            foreignKey = @ForeignKey(name = "fk_group_purchase_image_group_purchase"))
    private GroupPurchase groupPurchase;

    @Column(nullable = false, length = 255)
    private String imageKey;

    @Column(nullable = false)
    private int sortOrder;

    @Builder
    private GroupPurchaseImage(GroupPurchase groupPurchase, String imageKey, int sortOrder) {
        this.groupPurchase = groupPurchase;
        this.imageKey = imageKey;
        this.sortOrder = sortOrder;
    }

    public boolean isThumbnail(){
        return sortOrder == THUMBNAIL_ORDER;
    }


}
