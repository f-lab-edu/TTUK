package com.ttuk.participation;

/**
 * 참여를 누가 취소했는지.
 * participation.canceled_by에는 BUYER(구매자 참여 취소)와 SYSTEM(정산 대조 정리)만 저장한다.
 * SELLER는 판매자가 공동구매를 취소한 경우로, 참여 행은 JOINED 그대로 두고 조회 응답에서만 계산한다.
 */
public enum CanceledBy {

    BUYER,
    SELLER,
    SYSTEM
}