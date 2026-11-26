package com.ttuk.participation.dto;

import com.ttuk.participation.MyParticipationStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record MyParticipationListRequest(

        MyParticipationStatus status,

        @PositiveOrZero
        Integer page,

        @Min(1) @Max(50)
        Integer size

) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    public MyParticipationListRequest{

        if(page == null){
            page = DEFAULT_PAGE;
        }
        if(size == null){
            size = DEFAULT_SIZE;
        }
    }

}
