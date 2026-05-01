package com.simplecd.service;

import com.simplecd.model.BuildServer;
import com.simplecd.model.BuildServerGroup;
import com.simplecd.model.BuildServerStatus;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class BuildPlatformService {

    private final Map<String, BuildServerGroup> groups = new ConcurrentHashMap<>();
    private final Map<String, BuildServer> servers = new ConcurrentHashMap<>();

    // ── Group management ──────────────────────────────────────────────────────

    public BuildServerGroup createGroup(String name, String description) {
        BuildServerGroup g = new BuildServerGroup();
        g.setId(UUID.randomUUID().toString());
        g.setName(name);
        g.setDescription(description);
        g.setRegistrationToken(UUID.randomUUID().toString().replace("-", ""));
        g.setCreatedAt(LocalDateTime.now());
        groups.put(g.getId(), g);
        return g;
    }

    public Collection<BuildServerGroup> getAllGroups() { return groups.values(); }

    public BuildServerGroup getGroupById(String id) { return groups.get(id); }

    public BuildServerGroup getGroupByToken(String token) {
        if (token == null) return null;
        return groups.values().stream()
                .filter(g -> token.equals(g.getRegistrationToken()))
                .findFirst().orElse(null);
    }

    public boolean deleteGroup(String id) {
        if (!groups.containsKey(id)) return false;
        // remove all servers in this group
        servers.values().stream()
                .filter(s -> id.equals(s.getGroupId()))
                .map(BuildServer::getId)
                .collect(Collectors.toList())
                .forEach(servers::remove);
        groups.remove(id);
        return true;
    }

    public String regenerateToken(String groupId) {
        BuildServerGroup g = groups.get(groupId);
        if (g == null) return null;
        String newToken = UUID.randomUUID().toString().replace("-", "");
        g.setRegistrationToken(newToken);
        return newToken;
    }

    // ── Server management ─────────────────────────────────────────────────────

    /** Self-registration via token (called from the registration command on the build server) */
    public BuildServer registerByToken(String token, String agentName, String os,
                                       String type, String shell, String host,
                                       int port, String username, String sshKeyPath,
                                       String containerOrPod, String namespace, String workDir) {
        BuildServerGroup g = getGroupByToken(token);
        if (g == null) throw new IllegalArgumentException("Invalid registration token.");
        return doRegister(g, agentName, os, type, shell, host, port, username, sshKeyPath, containerOrPod, namespace, workDir);
    }

    /** Self-registration via user PAT (auth handled by AuthInterceptor, group identified by groupId) */
    public BuildServer registerToGroup(String groupId, String agentName, String os,
                                       String type, String shell, String host,
                                       int port, String username, String sshKeyPath,
                                       String containerOrPod, String namespace, String workDir) {
        BuildServerGroup g = groups.get(groupId);
        if (g == null) throw new IllegalArgumentException("Group not found: " + groupId);
        return doRegister(g, agentName, os, type, shell, host, port, username, sshKeyPath, containerOrPod, namespace, workDir);
    }

    private BuildServer doRegister(BuildServerGroup g, String agentName, String os,
                                   String type, String shell, String host,
                                   int port, String username, String sshKeyPath,
                                   String containerOrPod, String namespace, String workDir) {
        BuildServer s = new BuildServer();
        s.setId(UUID.randomUUID().toString());
        s.setGroupId(g.getId());
        s.setGroupName(g.getName());
        s.setAgentName(agentName != null ? agentName : "agent-" + s.getId().substring(0, 6));
        s.setOs(os != null ? os.toUpperCase() : "LINUX");
        s.setType(type != null ? type.toUpperCase() : "LOCAL");
        s.setShell(shell != null ? shell.toUpperCase() : "BASH");
        s.setHost(host);
        s.setPort(port > 0 ? port : 22);
        s.setUsername(username);
        s.setSshKeyPath(sshKeyPath);
        s.setContainerOrPod(containerOrPod);
        s.setNamespace(namespace);
        s.setWorkDir(resolveWorkDir(workDir, s.getAgentName(), s.getType()));
        s.setStatus(BuildServerStatus.ONLINE);
        s.setRegisteredAt(LocalDateTime.now());
        provisionWorkDir(s);
        servers.put(s.getId(), s);
        return s;
    }

    /** Manual add via UI */
    public BuildServer addServer(BuildServer s) {
        if (s.getId() == null || s.getId().isBlank()) s.setId(UUID.randomUUID().toString());
        s.setRegisteredAt(LocalDateTime.now());
        if (s.getStatus() == null) s.setStatus(BuildServerStatus.ONLINE);
        BuildServerGroup g = groups.get(s.getGroupId());
        if (g != null) s.setGroupName(g.getName());
        s.setWorkDir(resolveWorkDir(s.getWorkDir(), s.getAgentName(), s.getType()));
        provisionWorkDir(s);
        servers.put(s.getId(), s);
        return s;
    }

    /**
     * For LOCAL servers: if workDir is blank, derive a default under C:/SimpleCD/workspaces/<agentName>.
     * For remote servers (SSH/DOCKER/KUBERNETES): leave as-is.
     */
    private String resolveWorkDir(String workDir, String agentName, String type) {
        if ("LOCAL".equalsIgnoreCase(type) || type == null) {
            if (workDir == null || workDir.isBlank()) {
                String safeName = (agentName != null ? agentName : "agent")
                        .replaceAll("[^a-zA-Z0-9_\\-]", "_");
                return "C:/SimpleCD/workspaces/" + safeName;
            }
        }
        return workDir;
    }

    /**
     * Create the workspace directory on the local filesystem for LOCAL build servers.
     * If the directory already exists, this is a no-op.
     * Remote types (SSH/DOCKER/KUBERNETES) are skipped — the directory lives on the remote machine.
     */
    private void provisionWorkDir(BuildServer s) {
        String type = s.getType();
        if (!("LOCAL".equalsIgnoreCase(type) || type == null)) {
            // Remote — directory must be created on the target machine, not here.
            return;
        }
        String workDir = s.getWorkDir();
        if (workDir == null || workDir.isBlank()) return;
        try {
            Path dir = Paths.get(workDir);
            if (Files.exists(dir)) {
                // Already exists — nothing to do.
                s.setWorkDirNote("exists: " + dir.toAbsolutePath());
            } else {
                Files.createDirectories(dir);
                s.setWorkDirNote("created: " + dir.toAbsolutePath());
            }
        } catch (Exception e) {
            s.setWorkDirNote("error: " + e.getMessage());
        }
    }

    public Collection<BuildServer> getAllServers() { return servers.values(); }

    public List<BuildServer> getServersByGroup(String groupId) {
        return servers.values().stream()
                .filter(s -> groupId.equals(s.getGroupId()))
                .collect(Collectors.toList());
    }

    public BuildServer getServerById(String id) { return servers.get(id); }

    public boolean deleteServer(String id) { return servers.remove(id) != null; }

    // ── Ping ──────────────────────────────────────────────────────────────────

    public String ping(String serverId) {
        BuildServer s = servers.get(serverId);
        if (s == null) return "Build server not found";
        try {
            String[] probe = buildCommand(s, "echo SimpleCD-ping-ok");
            ProcessBuilder pb = new ProcessBuilder(probe);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append(" ");
            }
            boolean fin = p.waitFor(15, TimeUnit.SECONDS);
            if (!fin) { p.destroyForcibly(); s.setStatus(BuildServerStatus.OFFLINE); return "Ping timed out"; }
            int exit = p.exitValue();
            s.setLastPingAt(LocalDateTime.now());
            if (exit == 0) {
                if (s.getStatus() != BuildServerStatus.BUSY) s.setStatus(BuildServerStatus.ONLINE);
                return "OK - " + out.toString().trim();
            } else {
                s.setStatus(BuildServerStatus.OFFLINE);
                return "Ping failed (exit " + exit + "): " + out.toString().trim();
            }
        } catch (Exception ex) {
            s.setStatus(BuildServerStatus.OFFLINE);
            return "Ping error: " + ex.getMessage();
        }
    }

    // ── Command building ──────────────────────────────────────────────────────

    public String[] buildCommand(BuildServer s, String rawCommand) {
        String[] sp = shellPrefix(s.getShell());
        String type = s.getType() != null ? s.getType().toUpperCase() : "LOCAL";
        switch (type) {
            case "LOCAL": {
                String[] local = Arrays.copyOf(sp, sp.length + 1);
                local[sp.length] = rawCommand;
                return local;
            }
            case "SSH": {
                List<String> cmd = new ArrayList<>();
                cmd.add("ssh"); cmd.add("-o"); cmd.add("StrictHostKeyChecking=no"); cmd.add("-o"); cmd.add("BatchMode=yes");
                if (s.getSshKeyPath() != null && !s.getSshKeyPath().isBlank()) { cmd.add("-i"); cmd.add(s.getSshKeyPath().trim()); }
                cmd.add("-p"); cmd.add(String.valueOf(s.getPort() > 0 ? s.getPort() : 22));
                cmd.add(s.getUsername().trim() + "@" + s.getHost().trim());
                cmd.add(shellBin(s.getShell()) + " -c '" + rawCommand.replace("'", "'\\''") + "'");
                return cmd.toArray(new String[0]);
            }
            case "DOCKER": {
                List<String> cmd = new ArrayList<>();
                cmd.add("docker"); cmd.add("exec"); cmd.add(s.getContainerOrPod().trim());
                cmd.addAll(Arrays.asList(sp)); cmd.add(rawCommand);
                return cmd.toArray(new String[0]);
            }
            case "KUBERNETES": {
                List<String> cmd = new ArrayList<>();
                cmd.add("kubectl"); cmd.add("exec"); cmd.add(s.getContainerOrPod().trim());
                if (s.getNamespace() != null && !s.getNamespace().isBlank()) { cmd.add("-n"); cmd.add(s.getNamespace().trim()); }
                cmd.add("--"); cmd.addAll(Arrays.asList(sp)); cmd.add(rawCommand);
                return cmd.toArray(new String[0]);
            }
            default: throw new IllegalArgumentException("Unsupported type: " + type);
        }
    }

    private String shellBin(String shell) {
        if (shell == null) return "bash";
        switch (shell.toUpperCase()) {
            case "PWSH": return "pwsh";
            case "CMD":  return "cmd";
            default:     return "bash";
        }
    }

    private String[] shellPrefix(String shell) {
        if (shell == null) return new String[]{"bash", "-c"};
        switch (shell.toUpperCase()) {
            case "PWSH": return new String[]{"pwsh", "-NonInteractive", "-Command"};
            case "CMD":  return new String[]{"cmd", "/c"};
            default:     return new String[]{"bash", "-c"};
        }
    }
}