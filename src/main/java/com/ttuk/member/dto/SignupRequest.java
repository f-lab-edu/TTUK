package com.ttuk.member.dto;

import com.ttuk.member.Member;
import com.ttuk.member.MemberRole;
import jakarta.validation.constraints.*;

public record SignupRequest(

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(min = MemberPolicy.NICKNAME_MIN, max = MemberPolicy.NICKNAME_MAX, message = MemberPolicy.NICKNAME_MESSAGE)
        String nickname,

        @NotNull(message = "가입 유형을 선택해 주세요.")
        MemberRole role,

        @NotBlank(message = "연락처를 입력해 주세요.")
        @Pattern(regexp = MemberPolicy.PHONE_REGEX,
                message = MemberPolicy.PHONE_MESSAGE)
        String phoneNum,

        @NotBlank(message = "우편번호를 입력해 주세요.")
        @Pattern(regexp = MemberPolicy.ZIP_CODE_REGEX, message = MemberPolicy.ZIP_CODE_MESSAGE)
        String zipCode,

        @NotBlank(message = "주소를 입력해 주세요.")
        @Size(max = MemberPolicy.ADDRESS_MAX, message = MemberPolicy.ADDRESS_MESSAGE)
        String address,

        @Size(max = MemberPolicy.ADDRESS_DETAIL_MAX, message = MemberPolicy.ADDRESS_DETAIL_MESSAGE)
        String addressDetail

) {

    public Member toEntity(String encodedPassword){
        return Member.builder()
                .email(email)
                .encodedPassword(encodedPassword)
                .nickname(nickname)
                .role(role)
                .phoneNum(phoneNum)
                .zipCode(zipCode)
                .address(address)
                .addressDetail(addressDetail)
                .build();
    }


}
