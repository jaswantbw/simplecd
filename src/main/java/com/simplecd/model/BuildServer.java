package com.simplecd.model;
import java.time.LocalDateTime;

public class BuildServer {
    private String id;
    private String groupId;
    private String groupName;
    private String agentName;
    private String os;             // WINDOWS, LINUX, MACOS
    private String type;           // LOCAL, SSH, DOCKER, KUBERNETES
    private String shell;          // BASH, PWSH, CMD
    private String host;
    private int port;
    private String username;
    private String sshKeyPath;
    private String containerOrPod;
    private String namespace;
    private String workDir;
    private BuildServerStatus status;
    private LocalDateTime registeredAt;
    private LocalDateTime lastPingAt;
    /** Set at registration time: "created: <path>" | "exists: <path>" | "error: <msg>" | null for remote types */
    private String workDirNote;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getOs() { return os; }
    public void setOs(String os) { this.os = os; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getShell() { return shell; }
    public void setShell(String shell) { this.shell = shell; }
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
    public BuildServerStatus getStatus() { return status; }
    public void setStatus(BuildServerStatus status) { this.status = status; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
    public LocalDateTime getLastPingAt() { return lastPingAt; }
    public void setLastPingAt(LocalDateTime lastPingAt) { this.lastPingAt = lastPingAt; }
    public String getWorkDirNote() { return workDirNote; }
    public void setWorkDirNote(String workDirNote) { this.workDirNote = workDirNote; }
}