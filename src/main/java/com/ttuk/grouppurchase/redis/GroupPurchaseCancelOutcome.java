package com.ttuk.grouppurchase.redis;

public enum GroupPurchaseCancelOutcome {

    CANCELED(1, true),
    ALREADY_CANCELED(2, true),
    NOT_INITIALIZED(-1, false),
    NOT_OPEN(-2, false),
    INVALID_STATE(-3, false);

    private final long code;
    private final boolean carriesValues;

    GroupPurchaseCancelOutcome(long code, boolean carriesValues) {
        this.code = code;
        this.carriesValues = carriesValues;
    }

    public boolean carriesValues(){
        return carriesValues;
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