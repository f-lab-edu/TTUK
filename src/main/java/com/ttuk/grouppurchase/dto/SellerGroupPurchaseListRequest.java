package com.ttuk.grouppurchase.dto;

import com.ttuk.grouppurchase.GroupPurchaseStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record SellerGroupPurchaseListRequest(

        GroupPurchaseStatus status,

        @PositiveOrZero
        Integer page,

        @Min(1) @Max(50)
        Integer size

) {

    private static final int DEFAULT_PAGE  = 0;
    private static final int DEFAULT_SIZE = 20;

    public SellerGroupPurchaseListRequest {

        if(page == null){
            page = DEFAULT_PAGE;
        }
        if(size == null){
            size = DEFAULT_SIZE;
        }

    }

}
