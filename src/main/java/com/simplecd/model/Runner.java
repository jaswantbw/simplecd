// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
package com.simplecd.model;

import java.time.LocalDateTime;

/**
 * Represents a self-hosted runner that SimpleCd can dispatch builds to.
 *
 * Supported types:
 *   LOCAL      – same machine as SimpleCd (no extra credentials needed)
 *   SSH        – remote Linux/Windows server reachable via SSH
 *   DOCKER     – a running Docker container (docker exec)
 *   KUBERNETES – a running Kubernetes pod (kubectl exec)
 */
public class Runner {

    private String id;
    private String name;
    private RunnerType type;
    private RunnerShell shell;
    private RunnerStatus status;

    // SSH / remote fields
    private String host;
    private int port = 22;
    private String username;
    private String sshKeyPath;   // path to private key file (SSH type only)

    // Docker / Kubernetes fields
    private String containerOrPod;  // container name (DOCKER) or pod name (KUBERNETES)
    private String namespace;        // K8s namespace (KUBERNETES only)

    // Working directory on the runner where builds are staged
    private String workDir;

    // Free-form labels / tags (comma-separated), e.g. "linux,java,maven"
    private String labels;

    private LocalDateTime registeredAt;
    private LocalDateTime lastPingAt;

    // ---- constructors ----

    public Runner() {}

    public Runner(String id, String name, RunnerType type, RunnerShell shell, String workDir) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.shell = shell;
        this.workDir = workDir;
        this.status = RunnerStatus.ONLINE;
        this.registeredAt = LocalDateTime.now();
    }

    // ---- getters / setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public RunnerType getType() { return type; }
    public void setType(RunnerType type) { this.type = type; }

    public RunnerShell getShell() { return shell; }
    public void setShell(RunnerShell shell) { this.shell = shell; }

    public RunnerStatus getStatus() { return status; }
    public void setStatus(RunnerStatus status) { this.status = status; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getSshKeyPath() { return sshKeyPath; }
    public void setSshKeyPath(String sshKeyPath) { this.sshKeyPath = sshKeyPath; }

    public String getContainerOrPod() { return containerOrPod; }
    public void setContainerOrPod(String containerOrPod) { this.containerOrPod = containerOrPod; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getWorkDir() { return workDir; }
    public void setWorkDir(String workDir) { this.workDir = workDir; }

    public String getLabels() { return labels; }
    public void setLabels(String labels) { this.labels = labels; }

    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }

    public LocalDateTime getLastPingAt() { return lastPingAt; }
    public void setLastPingAt(LocalDateTime lastPingAt) { this.lastPingAt = lastPingAt; }
}
