// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
package com.simplecd.config;

import com.simplecd.service.UserTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTH_SESSION_KEY = "authenticated";

    private final UserTokenService userTokenService;

    public AuthInterceptor(UserTokenService userTokenService) {
        this.userTokenService = userTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // 1. Session-based authentication
        HttpSession session = request.getSession(false);
        Object authenticated = session == null ? null : session.getAttribute(AUTH_SESSION_KEY);
        if (Boolean.TRUE.equals(authenticated)) {
            return true;
        }

        // 2. Bearer token authentication (for API / build server callbacks)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String rawToken = authHeader.substring(7).trim();
            String userId = userTokenService.validateToken(rawToken);
            if (userId != null) {
                return true;
            }
        }

        // Redirect browsers to login; return 401 for API calls
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
        } else {
            response.sendRedirect("/login");
        }
        return false;
    }
}