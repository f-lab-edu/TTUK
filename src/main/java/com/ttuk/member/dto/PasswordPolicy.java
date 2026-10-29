package com.ttuk.member.dto;

public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{10,30}$";
    public static final String MESSAGE = "비밀번호는 영문과 숫자를 포함해 10~30자여야 합니다.";

    private PasswordPolicy() {
    }
}
