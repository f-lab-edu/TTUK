package com.ttuk.grouppurchase;


import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class GroupPurchaseSortConverter implements Converter<String, GroupPurchaseSort> {

    @Override
    public GroupPurchaseSort convert(String source){
        return GroupPurchaseSort.from(source);
    }

}
