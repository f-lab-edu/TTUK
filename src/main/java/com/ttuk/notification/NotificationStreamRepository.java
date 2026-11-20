package com.ttuk.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class NotificationStreamRepository {

    public static final String STREAM_KEY = "notification:stream";
    private static final long MAX_LENGTH = 10_000;

    private final StringRedisTemplate redisTemplate;

    public RecordId addNewGroupPurchase(Long groupPurchaseId, Long categoryId, String title) {

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("type", NotificationType.NEW_GROUP_PURCHASE.name());
        fields.put("groupPurchaseId", String.valueOf(groupPurchaseId));
        fields.put("categoryId", String.valueOf(categoryId));
        fields.put("title", title);

        MapRecord<String, String, String> message = StreamRecords.newRecord().in(STREAM_KEY).ofMap(fields);

        return redisTemplate.opsForStream().add(message, XAddOptions.trim(RedisStreamCommands.TrimOptions.maxLen(MAX_LENGTH).approximate()));

    }

}
