package com.ttuk.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    
    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 인증
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),

    // 회원
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다."),

    // 카테고리
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    ALREADY_SUBSCRIBED(HttpStatus.CONFLICT, "이미 구독한 카테고리입니다."),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "구독하지 않은 카테고리입니다."),

    // 공동구매
    INVALID_DISCOUNT_RULE(HttpStatus.BAD_REQUEST, "할인 규칙이 올바르지 않습니다."),
    INVALID_CLOSE_AT(HttpStatus.BAD_REQUEST, "마감 시각은 등록 시점 기준 1시간 뒤부터 7일 뒤까지 설정할 수 있습니다."),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "이미지는 1~5장, 장당 5MB 이하의 jpg·png·webp 파일만 등록할 수 있습니다."),
    GROUP_PURCHASE_NOT_FOUND(HttpStatus.NOT_FOUND, "공동구매를 찾을 수 없습니다."),

    GROUP_PURCHASE_NOT_OPEN(HttpStatus.CONFLICT, "참여할 수 없는 상태의 공동구매입니다."),
    GROUP_PURCHASE_FULL(HttpStatus.CONFLICT, "정원이 마감된 공동구매입니다."),
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 공동구매입니다."),

    ;

    private final HttpStatus status;
    private final String message;

}
