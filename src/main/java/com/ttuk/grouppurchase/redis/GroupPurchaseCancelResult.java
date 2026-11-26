package com.ttuk.grouppurchase.redis;

import java.util.List;

public record GroupPurchaseCancelResult(

        GroupPurchaseCancelOutcome outcome,
        Integer headcount,
        Integer price,
        Integer participantCount

) {

    private static final int SIZE_WITH_VALUES = 4;
    private static final int SIZE_WITHOUT_VALUES = 1;

    public static GroupPurchaseCancelResult from(List<Long> scriptResult){
        GroupPurchaseCancelOutcome outcome = GroupPurchaseCancelOutcome.from(scriptResult.get(0));
        int expectedSize = outcome.carriesValues() ? SIZE_WITH_VALUES : SIZE_WITHOUT_VALUES;
        if(scriptResult.size() != expectedSize){
            throw new IllegalStateException("공동구매 취소 Lua 결과 개수가 결과 종류와 맞지 않습니다. outcome="
                    + outcome + ", expected=" + expectedSize + ", actual=" + scriptResult.size());
        }
        if(!outcome.carriesValues()){
            return new GroupPurchaseCancelResult(outcome,null,null,null);
        }
        return new GroupPurchaseCancelResult(outcome,
                scriptResult.get(1).intValue(),
                scriptResult.get(2).intValue(),
                scriptResult.get(3).intValue());
    }

}