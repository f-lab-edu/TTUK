package com.ttuk.auth;

import com.ttuk.global.auth.Login;
import com.ttuk.global.auth.LoginMember;
import com.ttuk.global.auth.dto.LoginRequest;
import com.ttuk.global.auth.dto.LoginResponse;
import com.ttuk.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest httpRequest){

        LoginResponse response = authService.login(request);

        HttpSession oldSession = httpRequest.getSession(false);
        if(oldSession != null){
            oldSession.invalidate();
        }

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(LoginMember.SESSION_KEY, new LoginMember(response.memberId(), response.role()));

        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, String.valueOf(response.memberId()));

        return ResponseEntity.ok(ApiResponse.success(response));

    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Login LoginMember loginMember, HttpServletRequest httpRequest){
        HttpSession session = httpRequest.getSession(false);
        if(session != null){
            session.invalidate();
        }

        log.info("로그아웃 memberId : {}", loginMember.memberId());
        return ResponseEntity.noContent().build();
    }


}
