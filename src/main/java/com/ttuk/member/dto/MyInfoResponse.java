package com.ttuk.member.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ttuk.member.Member;
import com.ttuk.member.MemberRole;

import java.time.LocalDateTime;

public record MyInfoResponse(

        Long memberId,
        String email,
        String nickname,
        MemberRole role,
        String phoneNum,
        String zipCode,
        String address,
        String addressDetail,
        LocalDateTime createdAt

) {

    public static MyInfoResponse from(Member member) {
        return new MyInfoResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRole(),
                member.getPhoneNum(),
                member.getZipCode(),
                member.getAddress(),
                member.getAddressDetail(),
                member.getCreatedAt()
        );
    }

}
