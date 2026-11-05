package com.ttuk.global.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSessionManager {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public void expireAll(Long memberId){

        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(String.valueOf(memberId));

        for (String sessionId : sessions.keySet()) {
            sessionRepository.deleteById(sessionId);
        }

        log.info("회원 세션 전체 삭제 memberId : {}, 삭제 수 : {}", memberId, sessions.size());
    }


}
