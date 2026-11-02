package com.ttuk.member;

import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import com.ttuk.member.dto.*;
import com.ttuk.member.dto.MemberUpdateRequest.AddressRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void changePassword(Long memberId, PasswordChangeRequest request) {

        Member member = findMember(memberId);

        if (!passwordEncoder.matches(request.currentPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BusinessException(ErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        String encodedPassword = passwordEncoder.encode(request.newPassword());

        transactionTemplate.executeWithoutResult(status -> {
            Member target = findMember(memberId);
            target.changePassword(encodedPassword);
        });

        log.info("비밀번호 변경 memberId : {}", memberId);
    }

    public MyInfoResponse getMyInfo(Long memberId) {
        Member member = findMember(memberId);
        return MyInfoResponse.from(member);
    }

    @Transactional
    public MyInfoResponse updateMyInfo(Long memberId, MemberUpdateRequest request) {

        Member member = findMember(memberId);

        if(request.nickname() != null){
            member.changeNickName(request.nickname());
        }

        if (request.phoneNum() != null) {
            member.changePhoneNum(request.phoneNum());
        }

        if (request.address() != null) {
            AddressRequest address = request.address();
            member.changeAddress(address.zipCode(), address.address(), address.addressDetail());
        }

        log.info("내 정보 수정 memberId : {}", memberId);
        return MyInfoResponse.from(member);

    }

    private Member findMember(Long memberId){
        Member member = memberRepository.findById(memberId).orElse(null);
        if(member == null){
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return member;
    }

    private boolean isDuplicateEmail(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains("uk_member_email");
    }


}