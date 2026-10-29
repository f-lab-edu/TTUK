package com.ttuk.auth;

import com.ttuk.global.auth.dto.LoginRequest;
import com.ttuk.global.auth.dto.LoginResponse;
import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import com.ttuk.member.Member;
import com.ttuk.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request){

        Member member = memberRepository.findByEmail(request.email()).orElse(null);

        if(member == null || !passwordEncoder.matches(request.password(), member.getPassword())){
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        log.info("로그인 성공 memberId : {}", member.getId());
        return LoginResponse.from(member);

    }


}
