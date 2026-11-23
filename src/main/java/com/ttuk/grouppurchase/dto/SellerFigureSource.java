package com.ttuk.grouppurchase.dto;

import com.ttuk.grouppurchase.GroupPurchaseStatus;

import java.time.LocalDateTime;

public interface SellerFigureSource {

    Long groupPurchaseId();
    GroupPurchaseStatus status();
    LocalDateTime closeAt();
    int basePrice();
    Integer finalHeadcount();
    Integer finalPrice();

}
