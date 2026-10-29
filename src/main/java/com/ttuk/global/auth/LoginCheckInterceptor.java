package com.ttuk.global.auth;

import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler){
        HttpSession session = request.getSession(false);

        if(session == null  || session.getAttribute(LoginMember.SESSION_KEY) == null){
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return true;
    }



}
