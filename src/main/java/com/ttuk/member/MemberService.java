package com.ttuk.member;

import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import com.ttuk.member.dto.SignupRequest;
import com.ttuk.member.dto.SignupResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if(memberRepository.existsByEmail(request.email())){
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        Member member = request.toEntity(encodedPassword);

        try {
            memberRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateEmail(e)) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            throw e;
        }

        log.info("회원가입 완료 memberId : {}", member.getId());
        return new SignupResponse(member.getId());

    }

    private boolean isDuplicateEmail(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains("uk_member_email");
    }


}
