package com.ttuk.member;

import com.ttuk.global.auth.Login;
import com.ttuk.global.auth.LoginMember;
import com.ttuk.global.auth.LoginSessionManager;
import com.ttuk.global.response.ApiResponse;
import com.ttuk.member.dto.PasswordChangeRequest;
import com.ttuk.member.dto.SignupRequest;
import com.ttuk.member.dto.SignupResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;
    private final LoginSessionManager loginSessionManager;

    @PostMapping
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request){
        SignupResponse response = memberService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Login LoginMember loginMember,
                                               @Valid @RequestBody PasswordChangeRequest request,
                                               HttpServletRequest httpRequest){

        memberService.changePassword(loginMember.memberId(), request);

        loginSessionManager.expireAll(loginMember.memberId());

        HttpSession session = httpRequest.getSession(false);
        if(session != null){
            session.invalidate();
        }

        return ResponseEntity.noContent().build();
    }



}
