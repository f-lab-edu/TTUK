package com.ttuk.grouppurchase.redis;

public enum CancelOutcome {

    CANCELED(1),
    NOT_INITIALIZED(-1),
    NOT_JOINED(-2),
    NOT_OPEN(-3),
    DEADLINE_PASSED(-4),
    INVALID_HEADCOUNT(-5);

    private final long code;

    CancelOutcome(long code) {
        this.code = code;
    }

    public static CancelOutcome from(long code) {
        for(CancelOutcome outcome : values()){
            if(outcome.code == code) return outcome;
        }
        throw new IllegalStateException("알 수 없는 취소 결과 코드: " + code);
    }


}
