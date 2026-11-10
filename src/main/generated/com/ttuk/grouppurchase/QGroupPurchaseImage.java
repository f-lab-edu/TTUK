package com.ttuk.grouppurchase;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.dsl.StringTemplate;

import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.annotations.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QGroupPurchaseImage is a Querydsl query type for GroupPurchaseImage
 */
@SuppressWarnings("this-escape")
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QGroupPurchaseImage extends EntityPathBase<GroupPurchaseImage> {

    private static final long serialVersionUID = -152117228L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QGroupPurchaseImage groupPurchaseImage = new QGroupPurchaseImage("groupPurchaseImage");

    public final com.ttuk.global.entity.QBaseTimeEntity _super = new com.ttuk.global.entity.QBaseTimeEntity(this);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createdAt = _super.createdAt;

    public final QGroupPurchase groupPurchase;

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath imageKey = createString("imageKey");

    public final NumberPath<Integer> sortOrder = createNumber("sortOrder", Integer.class);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> updatedAt = _super.updatedAt;

    public QGroupPurchaseImage(String variable) {
        this(GroupPurchaseImage.class, forVariable(variable), INITS);
    }

    public QGroupPurchaseImage(Path<? extends GroupPurchaseImage> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QGroupPurchaseImage(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QGroupPurchaseImage(PathMetadata metadata, PathInits inits) {
        this(GroupPurchaseImage.class, metadata, inits);
    }

    public QGroupPurchaseImage(Class<? extends GroupPurchaseImage> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.groupPurchase = inits.isInitialized("groupPurchase") ? new QGroupPurchase(forProperty("groupPurchase"), inits.get("groupPurchase")) : null;
    }

}

