package com.ttuk.participation;

import com.ttuk.grouppurchase.GroupPurchaseStatus;

public enum MyParticipationStatus {

    OPEN,
    CONFIRMED,
    CANCELED;

    public static MyParticipationStatus of(ParticipationStatus participationStatus, GroupPurchaseStatus displayedGroupPurchaseStatus) {

        if (participationStatus == ParticipationStatus.CANCELED || displayedGroupPurchaseStatus == GroupPurchaseStatus.CANCELED) {
            return CANCELED;
        }
        if(displayedGroupPurchaseStatus == GroupPurchaseStatus.CLOSED){
            return CONFIRMED;
        }
        return OPEN;

    }

}
