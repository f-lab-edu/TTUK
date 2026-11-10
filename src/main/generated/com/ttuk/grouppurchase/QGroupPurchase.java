package com.ttuk.grouppurchase;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.dsl.StringTemplate;

import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.annotations.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QGroupPurchase is a Querydsl query type for GroupPurchase
 */
@SuppressWarnings("this-escape")
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QGroupPurchase extends EntityPathBase<GroupPurchase> {

    private static final long serialVersionUID = 880817543L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QGroupPurchase groupPurchase = new QGroupPurchase("groupPurchase");

    public final com.ttuk.global.entity.QBaseTimeEntity _super = new com.ttuk.global.entity.QBaseTimeEntity(this);

    public final NumberPath<Integer> basePrice = createNumber("basePrice", Integer.class);

    public final DateTimePath<java.time.LocalDateTime> canceledAt = createDateTime("canceledAt", java.time.LocalDateTime.class);

    public final StringPath cancelReason = createString("cancelReason");

    public final com.ttuk.category.QCategory category;

    public final DateTimePath<java.time.LocalDateTime> closeAt = createDateTime("closeAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> closedAt = createDateTime("closedAt", java.time.LocalDateTime.class);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createdAt = _super.createdAt;

    public final StringPath description = createString("description");

    public final NumberPath<Integer> discountStepSize = createNumber("discountStepSize", Integer.class);

    public final EnumPath<DiscountType> discountType = createEnum("discountType", DiscountType.class);

    public final NumberPath<Integer> discountValue = createNumber("discountValue", Integer.class);

    public final NumberPath<Integer> finalHeadcount = createNumber("finalHeadcount", Integer.class);

    public final NumberPath<Integer> finalPrice = createNumber("finalPrice", Integer.class);

    public final NumberPath<Integer> floorPrice = createNumber("floorPrice", Integer.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final NumberPath<Integer> maxHeadcount = createNumber("maxHeadcount", Integer.class);

    public final com.ttuk.member.QMember seller;

    public final EnumPath<GroupPurchaseStatus> status = createEnum("status", GroupPurchaseStatus.class);

    public final StringPath title = createString("title");

    public final StringPath unit = createString("unit");

    //inherited
    public final DateTimePath<java.time.LocalDateTime> updatedAt = _super.updatedAt;

    public QGroupPurchase(String variable) {
        this(GroupPurchase.class, forVariable(variable), INITS);
    }

    public QGroupPurchase(Path<? extends GroupPurchase> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QGroupPurchase(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QGroupPurchase(PathMetadata metadata, PathInits inits) {
        this(GroupPurchase.class, metadata, inits);
    }

    public QGroupPurchase(Class<? extends GroupPurchase> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.category = inits.isInitialized("category") ? new com.ttuk.category.QCategory(forProperty("category")) : null;
        this.seller = inits.isInitialized("seller") ? new com.ttuk.member.QMember(forProperty("seller")) : null;
    }

}

