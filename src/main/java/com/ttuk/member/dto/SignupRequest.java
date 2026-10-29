package com.ttuk.member.dto;

import com.ttuk.auth.dto.PasswordPolicy;
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
        @Size(min = 2, max = 15, message = "닉네임은 2~15자여야 합니다.")
        String nickname,

        @NotNull(message = "가입 유형을 선택해 주세요.")
        MemberRole role,

        @NotBlank(message = "연락처를 입력해 주세요.")
        @Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$",
                message = "연락처 형식이 올바르지 않습니다.")
        String phoneNum,

        @NotBlank(message = "우편번호를 입력해 주세요.")
        @Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리여야 합니다.")
        String zipCode,

        @NotBlank(message = "주소를 입력해 주세요.")
        @Size(max = 200, message = "주소는 200자 이하여야 합니다.")
        String address,

        @Size(max = 100, message = "상세 주소는 100자 이하여야 합니다.")
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
