package com.ttuk.grouppurchase.redis;

public enum GroupPurchaseCancelOutcome {

    CANCELED(1),
    ALREADY_CANCELED(2),
    NOT_INITIALIZED(-1),
    NOT_OPEN(-2),
    INVALID_STATE(-3);

    private final long code;

    GroupPurchaseCancelOutcome(long code) {
        this.code = code;
    }

    public boolean carriesValues(){
        return code > 0;
    }

    public static GroupPurchaseCancelOutcome from(long code){
        for(GroupPurchaseCancelOutcome outcome : values()){
            if(outcome.code == code){
                return outcome;
            }
        }
        throw new IllegalStateException("알 수 없는 공동구매 취소 결과 코드: " + code);
    }

}
