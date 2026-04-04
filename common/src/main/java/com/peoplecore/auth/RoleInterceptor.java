package com.peoplecore.auth;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RoleRequired roleRequired = handlerMethod.getMethodAnnotation(RoleRequired.class);
//        메서드에 없으면 클래스에서 찾기
        if(roleRequired == null){
            roleRequired = handlerMethod.getBeanType().getAnnotation(RoleRequired.class);
        }
        if (roleRequired == null) {
            return true;
        }

        String userRole = request.getHeader("X-User-Role");
        if (userRole == null || Arrays.stream(roleRequired.value()).noneMatch(userRole::equals)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return true;
    }
}