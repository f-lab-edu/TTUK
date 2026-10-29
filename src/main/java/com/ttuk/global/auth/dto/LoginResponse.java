package com.ttuk.global.auth.dto;

import com.ttuk.member.Member;
import com.ttuk.member.MemberRole;

public record LoginResponse(
        Long memberId,
        String nickname,
        MemberRole role
) {

    public static LoginResponse from(Member member) {
        return new LoginResponse(
                member.getId(),
                member.getNickname(),
                member.getRole()
        );
    }

}
