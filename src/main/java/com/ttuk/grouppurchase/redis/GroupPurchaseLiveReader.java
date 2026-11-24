package com.ttuk.grouppurchase.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupPurchaseLiveReader {

    private final GroupPurchaseRedisRepository redisRepository;

    public Map<Long, GroupPurchaseLiveValue> findLiveValues(List<Long> groupPurchaseIds){
        try {
            return redisRepository.findLiveValues(groupPurchaseIds);
        } catch (DataAccessException e) {
            log.error("Redis 인원·단가 조회 실패: DB 값으로 대신함. groupPurchaseIds={}", groupPurchaseIds, e);
            return Map.of();
        }
    }

    public GroupPurchaseLiveDetail findLiveDetail(Long groupPurchaseId, Long memberId){
        try {
            return redisRepository.findLiveDetail(groupPurchaseId, memberId);
        } catch (DataAccessException e) {
            log.error("Redis 상세 조회 실패: 인원·단가·상태를 DB 값으로 대신함. groupPurchaseId={}",
                    groupPurchaseId, e);
            return GroupPurchaseLiveDetail.EMPTY;
        }
    }

}
