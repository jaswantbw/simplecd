// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
package com.simplecd.service;
import com.simplecd.model.Runner;
import com.simplecd.model.RunnerShell;
import com.simplecd.model.RunnerStatus;
import com.simplecd.model.RunnerType;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class RunnerService {
    private final Map<String,Runner> runners = new ConcurrentHashMap<>();

    public Runner register(Runner r) {
        if (r.getId() == null || r.getId().isBlank()) r.setId(UUID.randomUUID().toString());
        r.setRegisteredAt(LocalDateTime.now());
        if (r.getStatus() == null) r.setStatus(RunnerStatus.ONLINE);
        runners.put(r.getId(), r);
        return r;
    }

    public Collection<Runner> getAll() { return runners.values(); }
    public Runner getById(String id) { return runners.get(id); }
    public boolean delete(String id) { return runners.remove(id) != null; }

    public Runner update(Runner u) {
        Runner e = runners.get(u.getId());
        if (e == null) return null;
        e.setName(u.getName()); e.setType(u.getType()); e.setShell(u.getShell()); e.setHost(u.getHost());
        e.setPort(u.getPort() > 0 ? u.getPort() : 22); e.setUsername(u.getUsername()); e.setSshKeyPath(u.getSshKeyPath());
        e.setContainerOrPod(u.getContainerOrPod()); e.setNamespace(u.getNamespace()); e.setWorkDir(u.getWorkDir()); e.setLabels(u.getLabels());
        return e;
    }

    public String ping(String id) {
        Runner runner = runners.get(id);
        if (runner == null) return "Runner not found";
        try {
            String[] probe = buildCommand(runner, "echo SimpleCD-ping-ok");
            ProcessBuilder pb = new ProcessBuilder(probe);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String l;
                while ((l = r.readLine()) != null) out.append(l).append(" ");
            }
            boolean fin = p.waitFor(15, TimeUnit.SECONDS);
            if (!fin) { p.destroyForcibly(); runner.setStatus(RunnerStatus.OFFLINE); return "Ping timed out"; }
            int exit = p.exitValue();
            runner.setLastPingAt(LocalDateTime.now());
            if (exit == 0) {
                if (runner.getStatus() != RunnerStatus.BUSY) runner.setStatus(RunnerStatus.ONLINE);
                return "OK - " + out.toString().trim();
            } else {
                runner.setStatus(RunnerStatus.OFFLINE);
                return "Ping failed (exit " + exit + "): " + out.toString().trim();
            }
        } catch (Exception ex) {
            runner.setStatus(RunnerStatus.OFFLINE);
            return "Ping error: " + ex.getMessage();
        }
    }

    public String[] buildCommand(Runner runner, String rawCommand) {
        String[] sp = shellPrefix(runner.getShell());
        switch (runner.getType()) {
            case LOCAL: {
                String[] local = Arrays.copyOf(sp, sp.length + 1);
                local[sp.length] = rawCommand;
                return local;
            }
            case SSH: {
                List<String> cmd = new ArrayList<>();
                cmd.add("ssh"); cmd.add("-o"); cmd.add("StrictHostKeyChecking=no"); cmd.add("-o"); cmd.add("BatchMode=yes");
                if (runner.getSshKeyPath() != null && !runner.getSshKeyPath().isBlank()) { cmd.add("-i"); cmd.add(runner.getSshKeyPath().trim()); }
                cmd.add("-p"); cmd.add(String.valueOf(runner.getPort() > 0 ? runner.getPort() : 22));
                cmd.add(runner.getUsername().trim() + "@" + runner.getHost().trim());
                cmd.add(shellBin(runner.getShell()) + " -c '" + rawCommand.replace("'", "'\\''") + "'");
                return cmd.toArray(new String[0]);
            }
            case DOCKER: {
                List<String> cmd = new ArrayList<>();
                cmd.add("docker"); cmd.add("exec"); cmd.add(runner.getContainerOrPod().trim());
                cmd.addAll(Arrays.asList(sp)); cmd.add(rawCommand);
                return cmd.toArray(new String[0]);
            }
            case KUBERNETES: {
                List<String> cmd = new ArrayList<>();
                cmd.add("kubectl"); cmd.add("exec"); cmd.add(runner.getContainerOrPod().trim());
                if (runner.getNamespace() != null && !runner.getNamespace().isBlank()) { cmd.add("-n"); cmd.add(runner.getNamespace().trim()); }
                cmd.add("--"); cmd.addAll(Arrays.asList(sp)); cmd.add(rawCommand);
                return cmd.toArray(new String[0]);
            }
            default: throw new IllegalArgumentException("Unsupported runner type: " + runner.getType());
        }
    }

    private String shellBin(RunnerShell shell) {
        if (shell == null) return "bash";
        switch (shell) {
            case PWSH: return "pwsh";
            case CMD:  return "cmd";
            default:   return "bash";
        }
    }

    private String[] shellPrefix(RunnerShell shell) {
        if (shell == null) return new String[]{"bash", "-c"};
        switch (shell) {
            case PWSH: return new String[]{"pwsh", "-NonInteractive", "-Command"};
            case CMD:  return new String[]{"cmd", "/c"};
            default:   return new String[]{"bash", "-c"};
        }
    }
}