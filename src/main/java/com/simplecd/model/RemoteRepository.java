package com.simplecd.model;

public class RemoteRepository {
    private String name;
    private String fullName;
    private String url;
    private String defaultBranch;
    private boolean privateRepo;

    public RemoteRepository() {
    }

    public RemoteRepository(String name, String fullName, String url, String defaultBranch, boolean privateRepo) {
        this.name = name;
        this.fullName = fullName;
        this.url = url;
        this.defaultBranch = defaultBranch;
        this.privateRepo = privateRepo;
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
}
