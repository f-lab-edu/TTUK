package com.ttuk.grouppurchase;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GroupPurchaseSort {

    CLOSE_AT("closeAt"),
    LATEST("latest");

    private final String value;

    public static GroupPurchaseSort from(String value){

        for(GroupPurchaseSort sort: values()){
            if(sort.value.equals(value)){
                return sort;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 정렬 기준: " + value);
    }

}
