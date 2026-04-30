// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
package com.simplecd.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTH_SESSION_KEY = "authenticated";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        HttpSession session = request.getSession(false);
        Object authenticated = session == null ? null : session.getAttribute(AUTH_SESSION_KEY);
        if (Boolean.TRUE.equals(authenticated)) {
            return true;
        }

        response.sendRedirect("/login");
        return false;
    }
}