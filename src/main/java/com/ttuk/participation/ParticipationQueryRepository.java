package com.ttuk.participation;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ttuk.participation.dto.MyParticipationListRequest;
import com.ttuk.participation.dto.MyParticipationRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static com.ttuk.grouppurchase.GroupPurchaseConditions.*;
import static com.ttuk.grouppurchase.QGroupPurchase.groupPurchase;
import static com.ttuk.participation.QParticipation.participation;

@Repository
@RequiredArgsConstructor
public class ParticipationQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<MyParticipationRow> findMyList(Long memberId, MyParticipationListRequest request, LocalDateTime now){

        return queryFactory.select(Projections.constructor(MyParticipationRow.class,
                participation.id,
                groupPurchase.id,
                groupPurchase.title,
                groupPurchase.unit,
                participation.status,
                participation.quantity,
                participation.joinedAt,
                participation.canceledAt,
                groupPurchase.status,
                groupPurchase.basePrice,
                groupPurchase.closeAt,
                groupPurchase.finalPrice,
                groupPurchase.cancelReason,
                groupPurchase.canceledAt))
                .from(participation)
                .join(participation.groupPurchase, groupPurchase)
                .where(participation.member.id.eq(memberId), myStatusEq(request.status(), now))
                .orderBy(participation.id.desc())
                .offset((long) request.page() * request.size())
                .limit(request.size())
                .fetch();

    }

    public long countMyList(Long memberId, MyParticipationStatus status, LocalDateTime now){

        Long count = queryFactory.select(participation.count())
                .from(participation)
                .join(participation.groupPurchase, groupPurchase)
                .where(participation.member.id.eq(memberId), myStatusEq(status, now))
                .fetchOne();

        return count == null ? 0 : count;

    }

    private BooleanExpression myStatusEq(MyParticipationStatus status, LocalDateTime now){

        if(status == null){
            return null;
        }

        return switch (status){
            case OPEN -> participation.status.eq(ParticipationStatus.JOINED).and(isOpenAt(now));
            case CONFIRMED -> participation.status.eq(ParticipationStatus.JOINED).and(isClosedAt(now));
            case CANCELED -> participation.status.eq(ParticipationStatus.CANCELED).or(isCanceled());
        };

    }


}
