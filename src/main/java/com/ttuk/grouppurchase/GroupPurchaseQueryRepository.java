package com.ttuk.grouppurchase;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ttuk.grouppurchase.dto.GroupPurchaseListRequest;
import com.ttuk.grouppurchase.dto.GroupPurchaseListRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ttuk.category.QCategory.category;
import static com.ttuk.grouppurchase.QGroupPurchase.groupPurchase;
import static com.ttuk.grouppurchase.QGroupPurchaseImage.groupPurchaseImage;

@Repository
@RequiredArgsConstructor
public class GroupPurchaseQueryRepository {

    private static final int THUMBNAIL_ORDER = 0;

    private final JPAQueryFactory queryFactory;

    public List<GroupPurchaseListRow> findopenList(GroupPurchaseListRequest request){

        return queryFactory
                .select(Projections.constructor(GroupPurchaseListRow.class,
                        groupPurchase.id,
                        groupPurchase.title,
                        category.name,
                        groupPurchase.basePrice,
                        groupPurchase.maxHeadcount,
                        groupPurchase.closeAt,
                        groupPurchase.status,
                        groupPurchaseImage.imageKey))
                .from(groupPurchase)
                .join(groupPurchase.category, category)
                .leftJoin(groupPurchaseImage).on(
                        groupPurchaseImage.groupPurchase.eq(groupPurchase),
                        groupPurchaseImage.sortOrder.eq(THUMBNAIL_ORDER))
                .where(groupPurchase.status.eq(GroupPurchaseStatus.OPEN),
                        categoryIdEq(request.categoryId()))
                .orderBy(orderBy(request.sort()))
                .offset((long) request.page() * request.size())
                .limit(request.size())
                .fetch();

    }

    public long countOpen(Long categoryId){
        Long count = queryFactory
                .select(groupPurchase.count())
                .from(groupPurchase)
                .where(groupPurchase.status.eq(GroupPurchaseStatus.OPEN),
                        categoryIdEq(categoryId))
                .fetchOne();
        return count == null ? 0 : count;
    }

    private BooleanExpression categoryIdEq(Long categoryId){
        return categoryId == null ? null : groupPurchase.category.id.eq(categoryId);
    }

    private OrderSpecifier<?>[] orderBy(GroupPurchaseSort sort){
        return switch (sort){
            case CLOSE_AT -> new OrderSpecifier<?>[]{groupPurchase.closeAt.asc(), groupPurchase.id.asc()};
            case LATEST -> new  OrderSpecifier<?>[]{groupPurchase.createdAt.desc()};
        };
    }

}
