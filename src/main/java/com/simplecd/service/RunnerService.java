// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
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

    private final Map<String, Runner> runners = new ConcurrentHashMap<>();

    // ---- CRUD ----

    public Runner register(Runner runner) {
        if (runner.getId() == null || runner.getId().isBlank()) {
            runner.setId(UUID.randomUUID().toString());
        }
        runner.setRegisteredAt(LocalDateTime.now());
        if (runner.getStatus() == null) {
            runner.setStatus(RunnerStatus.ONLINE);
        }
        runners.put(runner.getId(), runner);
        return runner;
    }

    public Collection<Runner> getAll() {
        return runners.values();
    }

    public Runner getById(String id) {
        return runners.get(id);
    }

    public boolean delete(String id) {
        return runners.remove(id) != null;
    }

    public Runner update(Runner updated) {
        Runner existing = runners.get(updated.getId());
        if (existing == null) return null;
        existing.setName(updated.getName());
        existing.setType(updated.getType());
        existing.setShell(updated.getShell());
        existing.setHost(updated.getHost());
        existing.setPort(updated.getPort() > 0 ? updated.getPort() : 22);
        existing.setUsername(updated.getUsername());
        existing.setSshKeyPath(updated.getSshKeyPath());
        existing.setContainerOrPod(updated.getContainerOrPod());
        existing.setNamespace(updated.getNamespace());
        existing.setWorkDir(updated.getWorkDir());
        existing.setLabels(updated.getLabels());
        return existing;
    }

    // ---- Ping / health check ----

    /**
     * Pings the runner with a no-op command and updates its status.
     * Returns the ping result message.
     */
    public String ping(String id) {
        Runner runner = runners.get(id);
        if (runner == null) return "Runner not found";

        try {
            String[] probe = buildProbeCommand(runner);
            ProcessBuilder pb = new ProcessBuilder(probe);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append(" ");
                }
            }

            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                runner.setStatus(RunnerStatus.OFFLINE);
                return "Ping timed out";
            }

            int exitCode = process.exitValue();
            runner.setLastPingAt(LocalDateTime.now());
            if (exitCode == 0) {
                if (runner.getStatus() != RunnerStatus.BUSY) {
                    runner.setStatus(RunnerStatus.ONLINE);
                }
                return "OK — " + out.toString().trim();
            } else {
                runner.setStatus(RunnerStatus.OFFLINE);
                return "Ping failed (exit " + exitCode + "): " + out.toString().trim();
            }
        } catch (Exception ex) {
            runner.setStatus(RunnerStatus.OFFLINE);
            return "Ping error: " + ex.getMessage();
        }
    }

    // ---- Command building helpers (used by BuildExecutorService) ----

    /**
     * Wraps a raw command string into the appropriate launch command for this runner.
     * For LOCAL: runs using the configured shell directly.
     * For SSH:   ssh -i key -p port user@host "<shell> -c '<cmd>'"
     * For DOCKER: docker exec <container> <shell> -c "<cmd>"
     * For K8S:   kubectl exec <pod> -n <ns> -- <shell> -c "<cmd>"
     */
    public String[] buildCommand(Runner runner, String rawCommand) {
        String[] shellPrefix = shellPrefix(runner.getShell());
        switch (runner.getType()) {
            case LOCAL:
                // shellPrefix + rawCommand
                String[] local = Arrays.copyOf(shellPrefix, shellPrefix.length + 1);
                local[shellPrefix.length] = rawCommand;
                return local;

            case SSH: {
                // ssh [-i key] -p port user@host "<shellBin> -c '<rawCommand>'"
                List<String> cmd = new ArrayList<>();
                cmd.add("ssh");
                cmd.add("-o"); cmd.add("StrictHostKeyChecking=no");
                cmd.add("-o"); cmd.add("BatchMode=yes");
                if (runner.getSshKeyPath() != null && !runner.getSshKeyPath().isBlank()) {
                    cmd.add("-i"); cmd.add(runner.getSshKeyPath().trim());
                }
                cmd.add("-p"); cmd.add(String.valueOf(runner.getPort() > 0 ? runner.getPort() : 22));
                cmd.add(runner.getUsername().trim() + "@" + runner.getHost().trim());
                cmd.add(shellBin(runner.getShell()) + " -c '" + rawCommand.replace("'", "'\\''") + "'");
                return cmd.toArray(new String[0]);
            }

            case DOCKER: {
                List<String> cmd = new ArrayList<>();
                cmd.add("docker"); cmd.add("exec");
                cmd.add(runner.getContainerOrPod().trim());
                cmd.addAll(Arrays.asList(shellPrefix));
                cmd.add(rawCommand);
                return cmd.toArray(new String[0]);
            }

            case KUBERNETES: {
                List<String> cmd = new ArrayList<>();
                cmd.add("kubectl"); cmd.add("exec");
                cmd.add(runner.getContainerOrPod().trim());
                if (runner.getNamespace() != null && !runner.getNamespace().isBlank()) {
                    cmd.add("-n"); cmd.add(runner.getNamespace().trim());
                }
                cmd.add("--");
                cmd.addAll(Arrays.asList(shellPrefix));
                cmd.add(rawCommand);
                return cmd.toArray(new String[0]);
            }

            default:
                throw new IllegalArgumentException("Unsupported runner type: " + runner.getType());
        }
    }

    /**
     * Builds a simple echo probe to test connectivity.
     */
    private String[] buildProbeCommand(Runner runner) {
        return buildCommand(runner, "echo SimpleCD-ping-ok");
    }

    // ---- Shell helpers ----

    /** Returns the shell binary name for -c invocations. */
    private String shellBin(RunnerShell shell) {
        if (shell == null) return "bash";
        return switch (shell) {
            case PWSH -> "pwsh";
            case CMD  -> "cmd";
            case BASH -> "bash";
        };
    }

    /**
     * Returns the argument prefix array for the shell (everything before the command string).
     * LOCAL / non-SSH runners use this directly in ProcessBuilder.
     */
    private String[] shellPrefix(RunnerShell shell) {
        if (shell == null) return new String[]{"bash", "-c"};
        return switch (shell) {
            case PWSH -> new String[]{"pwsh", "-NonInteractive", "-Command"};
            case CMD  -> new String[]{"cmd", "/c"};
            case BASH -> new String[]{"bash", "-c"};
        };
    }
}
