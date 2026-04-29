package com.simplecd.model;

public class RemoteRepository {
    private String name;
    private String fullName;
    private String url;
    private String defaultBranch;
    private boolean privateRepo;
    private String providerId;
    private String providerName;
    private String providerType;

    public RemoteRepository() {
    }

    public RemoteRepository(String name,
                            String fullName,
                            String url,
                            String defaultBranch,
                            boolean privateRepo,
                            String providerId,
                            String providerName,
                            String providerType) {
        this.name = name;
        this.fullName = fullName;
        this.url = url;
        this.defaultBranch = defaultBranch;
        this.privateRepo = privateRepo;
        this.providerId = providerId;
        this.providerName = providerName;
        this.providerType = providerType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public boolean isPrivateRepo() {
        return privateRepo;
    }

    public void setPrivateRepo(boolean privateRepo) {
        this.privateRepo = privateRepo;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }
}
