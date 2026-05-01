// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
package com.simplecd.model;
import java.time.LocalDateTime;
public class Runner {
    private String id; private String name; private RunnerType type; private RunnerShell shell; private RunnerStatus status;
    private String host; private int port = 22; private String username; private String sshKeyPath;
    private String containerOrPod; private String namespace; private String workDir; private String labels;
    private LocalDateTime registeredAt; private LocalDateTime lastPingAt;
    public Runner() {}
    public Runner(String id, String name, RunnerType type, RunnerShell shell, String workDir) {
        this.id=id; this.name=name; this.type=type; this.shell=shell; this.workDir=workDir;
        this.status=RunnerStatus.ONLINE; this.registeredAt=LocalDateTime.now();
    }
    public String getId(){return id;} public void setId(String id){this.id=id;}
    public String getName(){return name;} public void setName(String name){this.name=name;}
    public RunnerType getType(){return type;} public void setType(RunnerType type){this.type=type;}
    public RunnerShell getShell(){return shell;} public void setShell(RunnerShell shell){this.shell=shell;}
    public RunnerStatus getStatus(){return status;} public void setStatus(RunnerStatus status){this.status=status;}
    public String getHost(){return host;} public void setHost(String host){this.host=host;}
    public int getPort(){return port;} public void setPort(int port){this.port=port;}
    public String getUsername(){return username;} public void setUsername(String username){this.username=username;}
    public String getSshKeyPath(){return sshKeyPath;} public void setSshKeyPath(String sshKeyPath){this.sshKeyPath=sshKeyPath;}
    public String getContainerOrPod(){return containerOrPod;} public void setContainerOrPod(String c){this.containerOrPod=c;}
    public String getNamespace(){return namespace;} public void setNamespace(String namespace){this.namespace=namespace;}
    public String getWorkDir(){return workDir;} public void setWorkDir(String workDir){this.workDir=workDir;}
    public String getLabels(){return labels;} public void setLabels(String labels){this.labels=labels;}
    public LocalDateTime getRegisteredAt(){return registeredAt;} public void setRegisteredAt(LocalDateTime r){this.registeredAt=r;}
    public LocalDateTime getLastPingAt(){return lastPingAt;} public void setLastPingAt(LocalDateTime l){this.lastPingAt=l;}
}