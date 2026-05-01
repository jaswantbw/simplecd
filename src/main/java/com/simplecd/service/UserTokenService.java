// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
package com.simplecd.service;

import com.simplecd.model.UserToken;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class UserTokenService {

    private final Map<String, UserToken> tokensById = new ConcurrentHashMap<>();
    private final Map<String, UserToken> tokensByValue = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * Generate a new PAT for the given user.
     * Token format: scd_ + 40 lowercase hex chars (160 bits of entropy).
     */
    public UserToken generateToken(String userId, String name) {
        UserToken t = new UserToken();
        t.setId(UUID.randomUUID().toString().replace("-", ""));
        t.setUserId(userId);
        t.setName(name);

        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder("scd_");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        t.setToken(sb.toString());
        t.setCreatedAt(LocalDateTime.now());

        tokensById.put(t.getId(), t);
        tokensByValue.put(t.getToken(), t);
        return t;
    }

    /** List all tokens belonging to a user, newest first. */
    public List<UserToken> listTokens(String userId) {
        return tokensById.values().stream()
                .filter(t -> userId.equals(t.getUserId()))
                .sorted(Comparator.comparing(UserToken::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Revoke (delete) a token by ID. Only the owning user may revoke.
     * Returns true if the token was found and removed.
     */
    public boolean revokeToken(String userId, String tokenId) {
        UserToken t = tokensById.get(tokenId);
        if (t != null && userId.equals(t.getUserId())) {
            tokensById.remove(tokenId);
            tokensByValue.remove(t.getToken());
            return true;
        }
        return false;
    }

    /**
     * Validate a raw Bearer token value.
     * Updates lastUsedAt and returns the associated userId, or null if invalid.
     */
    public String validateToken(String rawToken) {
        if (rawToken == null || !rawToken.startsWith("scd_")) {
            return null;
        }
        UserToken t = tokensByValue.get(rawToken);
        if (t != null) {
            t.setLastUsedAt(LocalDateTime.now());
            return t.getUserId();
        }
        return null;
    }
}
