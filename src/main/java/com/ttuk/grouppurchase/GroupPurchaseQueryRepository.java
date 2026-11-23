package com.ttuk.grouppurchase;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ttuk.grouppurchase.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.ttuk.category.QCategory.category;
import static com.ttuk.grouppurchase.QGroupPurchase.groupPurchase;
import static com.ttuk.grouppurchase.QGroupPurchaseImage.groupPurchaseImage;
import static com.ttuk.member.QMember.member;

@Repository
@RequiredArgsConstructor
public class GroupPurchaseQueryRepository {

    private static final int THUMBNAIL_ORDER = 0;

    private final JPAQueryFactory queryFactory;


    public List<GroupPurchaseListRow> findOpenList(GroupPurchaseListRequest request, LocalDateTime now) {

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
                .where(openAt(now),
                        categoryIdEq(request.categoryId()))
                .orderBy(orderBy(request.sort()))
                .offset((long) request.page() * request.size())
                .limit(request.size())
                .fetch();
    }

    public long countOpen(Long categoryId, LocalDateTime now) {
        Long count = queryFactory
                .select(groupPurchase.count())
                .from(groupPurchase)
                .where(openAt(now),
                        categoryIdEq(categoryId))
                .fetchOne();
        return count == null ? 0 : count;
    }

    public Optional<GroupPurchaseDetailRow> findDetail(Long id) {
        GroupPurchaseDetailRow row = queryFactory
                .select(Projections.constructor(GroupPurchaseDetailRow.class,
                        groupPurchase.id,
                        groupPurchase.title,
                        groupPurchase.description,
                        groupPurchase.unit,
                        category.name,
                        member.nickname,
                        groupPurchase.basePrice,
                        groupPurchase.floorPrice,
                        groupPurchase.discountType,
                        groupPurchase.discountValue,
                        groupPurchase.discountStepSize,
                        groupPurchase.maxHeadcount,
                        groupPurchase.closeAt,
                        groupPurchase.status))
                .from(groupPurchase)
                .join(groupPurchase.category, category)
                .join(groupPurchase.seller, member)
                .where(groupPurchase.id.eq(id))
                .fetchOne();

        return Optional.ofNullable(row);
    }

    public List<String> findImageKeys(Long groupPurchaseId) {
        return queryFactory
                .select(groupPurchaseImage.imageKey)
                .from(groupPurchaseImage)
                .where(groupPurchaseImage.groupPurchase.id.eq(groupPurchaseId))
                .orderBy(groupPurchaseImage.sortOrder.asc())
                .fetch();
    }

    public List<SellerGroupPurchaseRow> findSellerList(Long sellerId, SellerGroupPurchaseListRequest request,
                                                       LocalDateTime now) {
        return queryFactory
                .select(Projections.constructor(SellerGroupPurchaseRow.class,
                        groupPurchase.id,
                        groupPurchase.title,
                        groupPurchase.status,
                        groupPurchase.basePrice,
                        groupPurchase.maxHeadcount,
                        groupPurchase.finalHeadcount,
                        groupPurchase.finalPrice,
                        groupPurchase.closeAt,
                        groupPurchase.createdAt))
                .from(groupPurchase)
                .where(groupPurchase.seller.id.eq(sellerId),
                        displayStatusEq(request.status(), now))
                .orderBy(groupPurchase.id.desc())
                .offset((long) request.page() * request.size())
                .limit(request.size())
                .fetch();
    }

    public long countSellerList(Long sellerId, GroupPurchaseStatus status, LocalDateTime now) {

        Long count = queryFactory.select(groupPurchase.count())
                .from(groupPurchase)
                .where(groupPurchase.seller.id.eq(sellerId), displayStatusEq(status, now))
                .fetchOne();
        return count == null ? 0 : count;

    }

    public Optional<SellerGroupPurchaseDetailRow> findSellerDetail(Long id) {

        SellerGroupPurchaseDetailRow row = queryFactory
                .select(Projections.constructor(SellerGroupPurchaseDetailRow.class,
                        groupPurchase.id,
                        groupPurchase.seller.id,
                        groupPurchase.title,
                        category.name,
                        groupPurchase.unit,
                        groupPurchase.status,
                        groupPurchase.basePrice,
                        groupPurchase.floorPrice,
                        groupPurchase.discountType,
                        groupPurchase.discountValue,
                        groupPurchase.discountStepSize,
                        groupPurchase.maxHeadcount,
                        groupPurchase.finalHeadcount,
                        groupPurchase.finalPrice,
                        groupPurchase.closeAt,
                        groupPurchase.createdAt,
                        groupPurchase.closedAt,
                        groupPurchase.cancelReason,
                        groupPurchase.canceledAt))
                .from(groupPurchase)
                .join(groupPurchase.category, category)
                .where(groupPurchase.id.eq(id))
                .fetchOne();

        return Optional.ofNullable(row);

    }

    private BooleanExpression displayStatusEq(GroupPurchaseStatus status, LocalDateTime now) {

        if(status == null){
            return null;
        }
        return switch (status){
            case OPEN -> openAt(now);
            case CLOSED -> groupPurchase.status.eq(GroupPurchaseStatus.CLOSED)
                    .or(groupPurchase.status.eq(GroupPurchaseStatus.OPEN).and(groupPurchase.closeAt.loe(now)));
            case CANCELED -> groupPurchase.status.eq(GroupPurchaseStatus.CANCELED);

        };

    }

    private BooleanExpression openAt(LocalDateTime now) {
        return groupPurchase.status.eq(GroupPurchaseStatus.OPEN)
                .and(groupPurchase.closeAt.gt(now));
    }

    private BooleanExpression categoryIdEq(Long categoryId) {
        return categoryId == null ? null : groupPurchase.category.id.eq(categoryId);
    }

    private OrderSpecifier<?>[] orderBy(GroupPurchaseSort sort) {
        return switch (sort) {
            case CLOSE_AT -> new OrderSpecifier<?>[]{groupPurchase.closeAt.asc(), groupPurchase.id.asc()};
            case LATEST -> new OrderSpecifier<?>[]{groupPurchase.id.desc()};
        };
    }
}