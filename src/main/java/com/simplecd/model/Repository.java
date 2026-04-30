// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
package com.simplecd.model;

import java.time.LocalDateTime;

public class Repository {
    private String id;
    private String name;
    private String url;
    private String localPath;
    private LocalDateTime clonedAt;
    private String defaultBranch;
    private String providerType;

    public Repository() {}

    public Repository(String id, String name, String url, String localPath, String defaultBranch, String providerType) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.localPath = localPath;
        this.defaultBranch = defaultBranch;
        this.providerType = providerType;
        this.clonedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public LocalDateTime getClonedAt() { return clonedAt; }
    public void setClonedAt(LocalDateTime clonedAt) { this.clonedAt = clonedAt; }

    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }

    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
}
