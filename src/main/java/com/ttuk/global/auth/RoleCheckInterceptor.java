package com.ttuk.global.auth;

import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RoleCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        if(!(handler instanceof HandlerMethod handlerMethod)){
            return true;
        }

        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if(requireRole == null){
            return true;
        }

        HttpSession session = request.getSession(false);
        LoginMember loginMember = null;
        if(session != null){
          loginMember = (LoginMember) session.getAttribute(LoginMember.SESSION_KEY);
        }
        if(loginMember == null){
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if(loginMember.role() != requireRole.value()){
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return true;

    }


}
