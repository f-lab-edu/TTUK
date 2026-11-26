package com.ttuk.participation.dto;

import com.ttuk.participation.CanceledBy;
import com.ttuk.participation.MyParticipationStatus;

import java.time.LocalDateTime;

public record MyParticipationResponse(

        Long participationId,
        Long groupPurchaseId,
        String title,
        String unit,
        MyParticipationStatus status,
        int quantity,
        Integer currentPrice,
        Integer finalPrice,
        Long amount,
        LocalDateTime closeAt,
        LocalDateTime joinedAt,
        CanceledBy canceledBy,
        String cancelReason,
        LocalDateTime canceledAt

) {

    public static MyParticipationResponse live(MyParticipationRow row, MyParticipationStatus status, int currentPrice) {
        return new MyParticipationResponse(
                row.participationId(), row.groupPurchaseId(), row.title(), row.unit(),
                status, row.quantity(),
                currentPrice, null, null,
                row.closeAt(), row.joinedAt(),
                null, null, null);

    }

    public static MyParticipationResponse settled(MyParticipationRow row, int finalPrice, long amount) {
        return new MyParticipationResponse(
                row.participationId(), row.groupPurchaseId(), row.title(), row.unit(),
                MyParticipationStatus.CONFIRMED, row.quantity(),
                null, finalPrice, amount,
                row.closeAt(), row.joinedAt(),
                null, null, null);
    }

    public static MyParticipationResponse canceled(MyParticipationRow row, CanceledBy canceledBy,
                                                    String cancelReason, LocalDateTime canceledAt) {
        return new MyParticipationResponse(
                row.participationId(), row.groupPurchaseId(), row.title(), row.unit(),
                MyParticipationStatus.CANCELED, row.quantity(),
                null, null, null,
                row.closeAt(), row.joinedAt(),
                canceledBy, cancelReason, canceledAt);
    }

}