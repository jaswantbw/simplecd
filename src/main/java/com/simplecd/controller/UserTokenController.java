// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
package com.simplecd.controller;

import com.simplecd.model.UserToken;
import com.simplecd.service.UserTokenService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tokens")
public class UserTokenController {

    private final UserTokenService tokenService;

    public UserTokenController(UserTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping
    public List<UserToken> listTokens(HttpSession session) {
        String userId = resolveUser(session);
        List<UserToken> tokens = tokenService.listTokens(userId);
        // Mask the token value in list responses — show only last 4 chars
        tokens.forEach(t -> t.setToken("scd_****" + t.getToken().substring(t.getToken().length() - 4)));
        return tokens;
    }

    @PostMapping
    public ResponseEntity<?> createToken(@RequestBody Map<String, String> body, HttpSession session) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token name is required"));
        }
        String userId = resolveUser(session);
        UserToken t = tokenService.generateToken(userId, name.trim());
        // Return full token ONLY at creation time
        return ResponseEntity.ok(t);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> revokeToken(@PathVariable String id, HttpSession session) {
        String userId = resolveUser(session);
        boolean ok = tokenService.revokeToken(userId, id);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    private String resolveUser(HttpSession session) {
        Object username = session.getAttribute("username");
        return username != null ? username.toString() : "admin";
    }
}
