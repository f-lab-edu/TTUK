package com.ttuk.grouppurchase.redis;

public enum RollbackJoinOutcome {

    ROLLED_BACK(1),
    NOTHING_TO_ROLLBACK(0),
    SETTLED(2);

    private final long code;

    RollbackJoinOutcome(long code) {
        this.code = code;
    }

    public static RollbackJoinOutcome from(long code) {
        for (RollbackJoinOutcome outcome : values()) {
            if (outcome.code == code) {
                return outcome;
            }
        }
        throw new IllegalStateException("알 수 없는 참여 되돌리기 결과 코드: " + code);
    }

}