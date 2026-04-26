package com.simplecd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "simplecd")
public class SimpleCdProperties {

    private String baseDir;
    private String workspaceDir;
    private String logsDir;
    private String artifactsDir;

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public String getWorkspaceDir() {
        return workspaceDir;
    }

    public void setWorkspaceDir(String workspaceDir) {
        this.workspaceDir = workspaceDir;
    }

    public String getLogsDir() {
        return logsDir;
    }

    public void setLogsDir(String logsDir) {
        this.logsDir = logsDir;
    }

    public String getArtifactsDir() {
        return artifactsDir;
    }

    public void setArtifactsDir(String artifactsDir) {
        this.artifactsDir = artifactsDir;
    }
}
