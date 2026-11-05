package com.ttuk.global.auth;

import com.ttuk.member.MemberRole;

import java.io.Serializable;

public record LoginMember(
        Long memberId,
        MemberRole role
) implements Serializable {

    public static final String SESSION_KEY = "LOGIN_MEMBER";

}
