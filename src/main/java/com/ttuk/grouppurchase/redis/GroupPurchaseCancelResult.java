package com.ttuk.grouppurchase.redis;

import java.util.List;

public record GroupPurchaseCancelResult(

        GroupPurchaseCancelOutcome outcome,
        Integer headcount,
        Integer price,
        Integer participantCount

) {

    public static GroupPurchaseCancelResult from(List<Long> scriptResult){
        GroupPurchaseCancelOutcome outcome = GroupPurchaseCancelOutcome.from(scriptResult.get(0));
        if(!outcome.carriesValues()){
            return new GroupPurchaseCancelResult(outcome,null,null,null);
        }
        return new GroupPurchaseCancelResult(outcome,
                scriptResult.get(1).intValue(),
                scriptResult.get(2).intValue(),
                scriptResult.get(3).intValue());
    }

}
