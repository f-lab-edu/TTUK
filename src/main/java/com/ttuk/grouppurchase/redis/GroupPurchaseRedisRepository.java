package com.ttuk.grouppurchase.redis;

import com.ttuk.global.config.ClockConfig;
import com.ttuk.grouppurchase.event.GroupPurchaseCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.DefaultStringRedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class GroupPurchaseRedisRepository {

    private static final Duration KEEP_AFTER_CLOSE = Duration.ofDays(1);
    private static final String UNLIMITED = "";
    private static final RedisScript<Long> INIT_SCRIPT =
            RedisScript.of(new ClassPathResource("redis/init_group_purchase.lua"), Long.class);
    private final StringRedisTemplate redisTemplate;

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> JOIN_SCRIPT =
            RedisScript.of(new ClassPathResource("redis/join_group_purchase.lua"), List.class);

    public boolean initialize(GroupPurchaseCreatedEvent event){

        Long id = event.groupPurchaseId();
        LocalDateTime closeAt = event.closeAt();

        List<String> keys = List.of(GroupPurchaseRedisKeys.meta(id),
                GroupPurchaseRedisKeys.headcount(id),
                GroupPurchaseRedisKeys.price(id),
                GroupPurchaseRedisKeys.status(id)
        );

        Long result = redisTemplate.execute(INIT_SCRIPT, keys,
                String.valueOf(event.basePrice()),
                String.valueOf(event.floorPrice()),
                event.discountType().name(),
                String.valueOf(event.discountValue()),
                String.valueOf(event.discountStepSize()),
                event.maxHeadcount() == null ? UNLIMITED : String.valueOf(event.maxHeadcount()),
                String.valueOf(toEpochSecond(closeAt)),
                String.valueOf(toEpochSecond(closeAt.plus(KEEP_AFTER_CLOSE)))
        );

        return Long.valueOf(1L).equals(result);
    }

    public Map<Long, GroupPurchaseLiveValue> findLiveValues(List<Long> groupPurchaseIds){

        if(groupPurchaseIds.isEmpty()){
            return Map.of();
        }

        List<String> keys = new ArrayList<>(groupPurchaseIds.size() * 2);
        for(Long id : groupPurchaseIds){
            keys.add(GroupPurchaseRedisKeys.headcount(id));
            keys.add(GroupPurchaseRedisKeys.price(id));
        }

        List<String> values = redisTemplate.opsForValue().multiGet(keys);

        Map<Long, GroupPurchaseLiveValue> result = new HashMap<>(groupPurchaseIds.size());

        for(int i = 0; i < groupPurchaseIds.size(); i++){
            String headcount = values == null ? null : values.get(i * 2);
            String price = values == null ? null : values.get(i * 2 + 1);
            result.put(groupPurchaseIds.get(i), GroupPurchaseLiveValue.of(headcount, price));
        }

        return result;

    }

    public GroupPurchaseLiveDetail findLiveDetail(Long groupPurchaseId, Long memberId) {
        String headcountKey = GroupPurchaseRedisKeys.headcount(groupPurchaseId);
        String priceKey = GroupPurchaseRedisKeys.price(groupPurchaseId);
        String statusKey = GroupPurchaseRedisKeys.status(groupPurchaseId);
        String participantsKey = GroupPurchaseRedisKeys.participants(groupPurchaseId);

        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection stringConnection = new DefaultStringRedisConnection(connection);
            stringConnection.mGet(headcountKey, priceKey, statusKey);
            stringConnection.sIsMember(participantsKey, String.valueOf(memberId));
            return null;
        });

        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) results.get(0);
        Boolean joined = (Boolean) results.get(1);

        return GroupPurchaseLiveDetail.of(values.get(0), values.get(1), values.get(2), joined);
    }

    public JoinResult join(Long groupPurchaseId, Long memberId) {
        List<String> keys = List.of(
                GroupPurchaseRedisKeys.meta(groupPurchaseId),
                GroupPurchaseRedisKeys.headcount(groupPurchaseId),
                GroupPurchaseRedisKeys.price(groupPurchaseId),
                GroupPurchaseRedisKeys.status(groupPurchaseId),
                GroupPurchaseRedisKeys.participants(groupPurchaseId)
        );

        @SuppressWarnings("unchecked")
        List<Long> result = redisTemplate.execute(JOIN_SCRIPT, keys, String.valueOf(memberId));

        return JoinResult.from(result);
    }

    private long toEpochSecond(LocalDateTime dateTime) {
        return dateTime.atZone(ClockConfig.SERVICE_ZONE).toEpochSecond();
    }
}
