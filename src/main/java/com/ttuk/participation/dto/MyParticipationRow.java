package com.ttuk.participation.dto;

import com.ttuk.grouppurchase.GroupPurchaseStatus;
import com.ttuk.participation.ParticipationStatus;

import java.time.LocalDateTime;

public record MyParticipationRow(

        Long participationId,
        Long groupPurchaseId,
        String title,
        String unit,
        ParticipationStatus participationStatus,
        int quantity,
        LocalDateTime joinedAt,
        LocalDateTime participationCanceledAt,
        GroupPurchaseStatus groupPurchaseStatus,
        int basePrice,
        LocalDateTime closeAt,
        Integer finalPrice,
        String cancelReason,
        LocalDateTime groupPurchaseCanceledAt

) {



}
