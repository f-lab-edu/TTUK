package com.ttuk.member.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberUpdateRequest(

        @Size(min = MemberPolicy.NICKNAME_MIN, max = MemberPolicy.NICKNAME_MAX,
                message = MemberPolicy.NICKNAME_MESSAGE)
        @Pattern(regexp = ".*\\S.*", message = "닉네임은 공백만으로 이루어질 수 없습니다.")
        String nickname,

        @Pattern(regexp = MemberPolicy.PHONE_REGEX, message = MemberPolicy.PHONE_MESSAGE)
        String phoneNum,

        @Valid
        AddressRequest address

) {

    @AssertTrue(message = "수정할 항목을 하나 이상 입력해주세요.")
    public boolean isUpdateFieldPresent() {
        return nickname != null || phoneNum != null || address != null;
    }

    public record AddressRequest(

            @NotBlank(message = "우편번호를 입력해 주세요.")
            @Pattern(regexp = MemberPolicy.ZIP_CODE_REGEX, message = MemberPolicy.ZIP_CODE_MESSAGE)
            String zipCode,

            @NotBlank(message = "주소를 입력해 주세요.")
            @Size(max = MemberPolicy.ADDRESS_MAX, message = MemberPolicy.ADDRESS_MESSAGE)
            String address,

            @Size(max = MemberPolicy.ADDRESS_DETAIL_MAX, message = MemberPolicy.ADDRESS_DETAIL_MESSAGE)
            String addressDetail

    ) {
    }


}
