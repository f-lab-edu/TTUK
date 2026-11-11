package com.ttuk.grouppurchase.dto;

import com.ttuk.grouppurchase.GroupPurchaseSort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record GroupPurchaseListRequest(

        Long categoryId,

        @PositiveOrZero
        Integer page,

        @Min(1) @Max(50)
        Integer size,

        GroupPurchaseSort sort
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;

    public GroupPurchaseListRequest {

        if(page == null){
            page = DEFAULT_PAGE;
        }
        if(size == null){
            size = DEFAULT_SIZE;
        }
        if(sort == null){
            sort = GroupPurchaseSort.CLOSE_AT;
        }

    }

}
