package com.ttuk.member.dto;

public final class MemberPolicy {

    // 닉네임
    public static final int NICKNAME_MIN = 2;
    public static final int NICKNAME_MAX = 15;
    public static final String NICKNAME_MESSAGE = "닉네임은 2~15자여야 합니다.";

    // 연락처 (하이픈 있어도 되고 없어도 됨, 저장할 땐 숫자만)
    public static final String PHONE_REGEX = "^01[0-9]-?\\d{3,4}-?\\d{4}$";
    public static final String PHONE_MESSAGE = "연락처 형식이 올바르지 않습니다.";

    // 주소
    public static final String ZIP_CODE_REGEX = "^\\d{5}$";
    public static final String ZIP_CODE_MESSAGE = "우편번호는 숫자 5자리여야 합니다.";
    public static final int ADDRESS_MAX = 200;
    public static final String ADDRESS_MESSAGE = "주소는 200자 이하여야 합니다.";
    public static final int ADDRESS_DETAIL_MAX = 100;
    public static final String ADDRESS_DETAIL_MESSAGE = "상세 주소는 100자 이하여야 합니다.";

    private MemberPolicy() {
    }

}
