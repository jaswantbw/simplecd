// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
package com.simplecd.model;

public class ResolvedGitProfile {
    private final String providerType;
    private final String providerName;
    private final String providerUrl;
    private final String username;
    private final String password;
    private final String pat;

    public ResolvedGitProfile(String providerType,
                              String providerName,
                              String providerUrl,
                              String username,
                              String password,
                              String pat) {
        this.providerType = providerType;
        this.providerName = providerName;
        this.providerUrl = providerUrl;
        this.username = username;
        this.password = password;
        this.pat = pat;
    }

    public String getProviderType() {
        return providerType;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getProviderUrl() {
        return providerUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getPat() {
        return pat;
    }
}