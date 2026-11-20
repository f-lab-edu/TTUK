package com.ttuk.grouppurchase.redis;

import com.ttuk.grouppurchase.GroupPurchaseStatus;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class RedisValueParser {

    private RedisValueParser() {
    }

    static Integer toInteger(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            log.error("Redis 값이 정수가 아니어서 없는 값으로 처리함. value={}", value);
            return null;
        }
    }

    static GroupPurchaseStatus toStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return GroupPurchaseStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.error("Redis 상태 값을 알 수 없어서 없는 값으로 처리함. value={}", value);
            return null;
        }
    }
}