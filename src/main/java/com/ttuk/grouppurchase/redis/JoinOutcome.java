package com.ttuk.grouppurchase.redis;

public enum JoinOutcome {

    JOINED(1),
    NOT_INITIALIZED(-1),
    NOT_OPEN(-2),
    ALREADY_JOINED(-3),
    FULL(-4);

    private final long code;

    JoinOutcome(long code) {
        this.code = code;
    }

    public static JoinOutcome from(long code) {
        for(JoinOutcome outcome : values()) {
            if(outcome.code == code) {
                return outcome;
            }
        }
        throw new IllegalStateException("알 수 없는 참여 결과 코드: " + code);
    }


}
