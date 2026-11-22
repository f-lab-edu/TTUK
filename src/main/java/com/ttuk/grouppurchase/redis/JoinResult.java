package com.ttuk.grouppurchase.redis;

import java.util.List;

public record JoinResult(

        JoinOutcome outcome,
        Integer headcount,
        Integer price

) {

    public static JoinResult from(List<Long> scriptResult){

        JoinOutcome outcome = JoinOutcome.from(scriptResult.get(0));
        if(outcome != JoinOutcome.JOINED){
            return new JoinResult(outcome, null, null);
        }

        return new JoinResult(outcome,
                scriptResult.get(1).intValue(),
                scriptResult.get(2).intValue());

    }

    public boolean joined(){
        return outcome == JoinOutcome.JOINED;
    }
}
