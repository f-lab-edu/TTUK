package com.ttuk.grouppurchase.redis;

import com.ttuk.global.config.ClockConfig;
import com.ttuk.grouppurchase.event.GroupPurchaseCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
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

    private long toEpochSecond(LocalDateTime dateTime) {
        return dateTime.atZone(ClockConfig.SERVICE_ZONE).toEpochSecond();
    }
}
