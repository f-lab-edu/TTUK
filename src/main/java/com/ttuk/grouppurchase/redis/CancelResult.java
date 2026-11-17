package com.ttuk.grouppurchase.redis;

import java.util.List;

public record CancelResult(

        CancelOutcome outcome,
        Integer headcount,
        Integer price

) {

    public static CancelResult from(List<Long> scriptResult){

        CancelOutcome outcome = CancelOutcome.from(scriptResult.get(0));
        if(outcome != CancelOutcome.CANCELED) return new CancelResult(outcome, null, null);
        return new CancelResult(outcome, scriptResult.get(1).intValue(), scriptResult.get(2).intValue());

    }

}
