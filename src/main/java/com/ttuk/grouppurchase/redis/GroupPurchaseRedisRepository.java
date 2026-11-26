package com.ttuk.grouppurchase.redis;

import com.ttuk.global.config.ClockConfig;
import com.ttuk.grouppurchase.DiscountPolicy;
import com.ttuk.grouppurchase.GroupPurchaseStatus;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    private static final RedisScript<Long> ROLLBACK_JOIN_SCRIPT =
            RedisScript.of(new ClassPathResource("redis/rollback_join_group_purchase.lua"), Long.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> CANCEL_SCRIPT =
            RedisScript.of(new ClassPathResource("redis/cancel_join_group_purchase.lua"), List.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SETTLEMENT_SNAPSHOT_SCRIPT =
            RedisScript.of(new ClassPathResource("redis/take_settlement_snapshot.lua"), List.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> CANCEL_GROUP_PURCHASE_SCRIPT =
            RedisScript.of(new ClassPathResource("redis/cancel_group_purchase.lua"), List.class);

    private static final RedisScript<Long> REOPEN_GROUP_PURCHASE_SCRIPT =
            RedisScript.of(new ClassPathResource("redis/reopen_group_purchase.lua"), Long.class);


    public boolean initialize(GroupPurchaseCreatedEvent event) {
        return createKeys(event, Set.of());
    }

    public boolean restore(GroupPurchaseCreatedEvent event, Collection<Long> joinedMemberIds) {
        return createKeys(event, new HashSet<>(joinedMemberIds));
    }

    private boolean createKeys(GroupPurchaseCreatedEvent event, Set<Long> participantIds) {
        Long groupPurchaseId = event.groupPurchaseId();
        DiscountPolicy policy = new DiscountPolicy(event.basePrice(), event.floorPrice(),
                event.discountType(), event.discountValue(), event.discountStepSize());
        Integer maxHeadcount = event.maxHeadcount();

        int headcount = participantIds.size();
        GroupPurchaseStatus status = maxHeadcount != null && headcount >= maxHeadcount
                ? GroupPurchaseStatus.CLOSED
                : GroupPurchaseStatus.OPEN;

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(policy.basePrice()));
        args.add(String.valueOf(policy.floorPrice()));
        args.add(policy.discountType().name());
        args.add(String.valueOf(policy.discountValue()));
        args.add(String.valueOf(policy.discountStepSize()));
        args.add(maxHeadcount == null ? UNLIMITED : String.valueOf(maxHeadcount));
        args.add(String.valueOf(toEpochSecond(event.closeAt())));
        args.add(String.valueOf(toEpochSecond(event.closeAt().plus(KEEP_AFTER_CLOSE))));
        args.add(String.valueOf(headcount));
        args.add(String.valueOf(policy.priceAt(headcount)));
        args.add(status.name());
        for (Long memberId : participantIds) {
            args.add(String.valueOf(memberId));
        }

        Long result = redisTemplate.execute(INIT_SCRIPT, participationKeys(groupPurchaseId), args.toArray());
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
        @SuppressWarnings("unchecked")
        List<Long> result = redisTemplate.execute(JOIN_SCRIPT, participationKeys(groupPurchaseId),
                String.valueOf(memberId));
        return JoinResult.from(result);
    }

    public RollbackJoinOutcome rollbackJoin(Long groupPurchaseId, Long memberId) {
        Long result = redisTemplate.execute(ROLLBACK_JOIN_SCRIPT, participationKeys(groupPurchaseId),
                String.valueOf(memberId));
        return RollbackJoinOutcome.from(result);
    }

    public CancelResult cancel(Long groupPurchaseId, Long memberId, LocalDateTime now, Duration cancelableBefore) {

        @SuppressWarnings("unchecked")
        List<Long> result  = redisTemplate.execute(CANCEL_SCRIPT, participationKeys(groupPurchaseId),
                String.valueOf(memberId),
                String.valueOf(toEpochSecond(now)),
                String.valueOf(cancelableBefore.toSeconds()));
        return CancelResult.from(result);

    }

    public Optional<SettlementSnapshot> takeSettlementSnapshot(Long groupPurchaseId) {

        @SuppressWarnings("unchecked")
        List<String> result = redisTemplate.execute(SETTLEMENT_SNAPSHOT_SCRIPT, participationKeys(groupPurchaseId));

        if(result.isEmpty()){
            return Optional.empty();
        }

        return Optional.of(SettlementSnapshot.from(groupPurchaseId, result));

    }

    public GroupPurchaseCancelResult cancelGroupPurchase(Long groupPurchaseId) {

        @SuppressWarnings("unchecked")
        List<Long> result = redisTemplate.execute(CANCEL_GROUP_PURCHASE_SCRIPT,
                participationKeys(groupPurchaseId));
        return GroupPurchaseCancelResult.from(result);

    }

    public boolean reopenGroupPurchase(Long groupPurchaseId) {

        Long result = redisTemplate.execute(REOPEN_GROUP_PURCHASE_SCRIPT,
                List.of(GroupPurchaseRedisKeys.status(groupPurchaseId)));
        return Long.valueOf(1L).equals(result);

    }

    private List<String> participationKeys(Long groupPurchaseId) {

        return List.of(
                GroupPurchaseRedisKeys.meta(groupPurchaseId),
                GroupPurchaseRedisKeys.headcount(groupPurchaseId),
                GroupPurchaseRedisKeys.price(groupPurchaseId),
                GroupPurchaseRedisKeys.status(groupPurchaseId),
                GroupPurchaseRedisKeys.participants(groupPurchaseId));

    }

    private long toEpochSecond(LocalDateTime dateTime) {
        return dateTime.atZone(ClockConfig.SERVICE_ZONE).toEpochSecond();
    }
}