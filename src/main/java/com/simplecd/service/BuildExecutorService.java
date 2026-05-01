package com.simplecd.service;

import com.simplecd.model.BuildJob;
import com.simplecd.model.BuildServer;
import com.simplecd.model.BuildServerStatus;
import com.simplecd.model.BuildStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Comparator;
import java.util.stream.Stream;

@Service
public class BuildExecutorService {

    private final Path workspaceRoot = Paths.get("C:/SimpleCD/workspaces");
    private final Path logsRoot      = Paths.get("C:/SimpleCD/logs");
    private final Path artifactsRoot = Paths.get("C:/SimpleCD/artifacts");

    private final BuildPlatformService platformService;

    public BuildExecutorService(BuildPlatformService platformService) {
        this.platformService = platformService;
    }

    @Async
    public void executeBuild(BuildJob job) {
        Path workspace   = workspaceRoot.resolve(job.getJobId());
        Path logFile     = logsRoot.resolve(job.getJobId() + ".log");
        Path artifactDir = artifactsRoot.resolve(job.getJobId());
        job.setWorkspacePath(workspace.toString());
        job.setLogPath(logFile.toString());
        job.setArtifactPath(artifactDir.toString());

        BuildServer server = resolveServer(job);

        try {
            createBaseDirectories();
            cleanDirectory(workspace);
            Files.createDirectories(workspace);
            Files.createDirectories(artifactDir);
            job.setStatus(BuildStatus.RUNNING);
            if (server != null) server.setStatus(BuildServerStatus.BUSY);

            writeLog(logFile, "SimpleCD Build Started\n");
            writeLog(logFile, "Job ID       : " + job.getJobId() + "\n");
            writeLog(logFile, "Repository   : " + job.getRepoUrl() + "\n");
            writeLog(logFile, "Branch       : " + job.getBranch() + "\n");
            writeLog(logFile, "Build Server : " + (server == null
                    ? "LOCAL (default)"
                    : server.getAgentName() + " [" + server.getType() + "/" + server.getShell() + "] group=" + server.getGroupName()) + "\n\n");

            if (server == null || "LOCAL".equals(server.getType())) {
                runLocalCommand(new String[]{"git", "clone", "-b", job.getBranch(), job.getRepoUrl(), workspace.toString()}, logFile, null);
                runLocalCommand(new String[]{"mvn", "clean", "package"}, logFile, workspace.toFile());
                Path jarFile = findJarFile(workspace);
                if (jarFile == null) throw new RuntimeException("No JAR artifact found in target/ folder.");
                Path copiedArtifact = artifactDir.resolve(jarFile.getFileName());
                Files.copy(jarFile, copiedArtifact, StandardCopyOption.REPLACE_EXISTING);
                writeLog(logFile, "\nArtifact saved: " + copiedArtifact + "\n");
                job.setArtifactPath(copiedArtifact.toString());
            } else {
                String remoteWork = (server.getWorkDir() != null && !server.getWorkDir().isBlank()
                        ? server.getWorkDir() : "/tmp/simplecd") + "/" + job.getJobId();
                runOnServer(server, "mkdir -p " + remoteWork, logFile);
                runOnServer(server, "git clone -b \"" + job.getBranch() + "\" \"" + job.getRepoUrl() + "\" " + remoteWork, logFile);
                runOnServer(server, "cd " + remoteWork + " && mvn clean package", logFile);
                copyArtifactBack(server, remoteWork + "/target/*.jar", artifactDir, logFile);
            }

            job.setStatus(BuildStatus.SUCCESS);
            writeLog(logFile, "\n[OK] BUILD SUCCESS\n");

        } catch (Exception ex) {
            job.setStatus(BuildStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            try { writeLog(logFile, "\n[FAIL] BUILD FAILED: " + ex.getMessage() + "\n"); } catch (IOException ignored) {}
        } finally {
            job.setEndTime(LocalDateTime.now());
            if (server != null && server.getStatus() == BuildServerStatus.BUSY)
                server.setStatus(BuildServerStatus.ONLINE);
        }
    }

    private BuildServer resolveServer(BuildJob job) {
        if (job.getBuildServerId() != null && !job.getBuildServerId().isBlank())
            return platformService.getServerById(job.getBuildServerId());
        if (job.getGroupId() != null && !job.getGroupId().isBlank()) {
            List<BuildServer> inGroup = platformService.getServersByGroup(job.getGroupId());
            return inGroup.stream()
                    .filter(s -> s.getStatus() == BuildServerStatus.ONLINE)
                    .findFirst().orElse(inGroup.isEmpty() ? null : inGroup.get(0));
        }
        return null;
    }

    private void runLocalCommand(String[] cmd, Path logFile, File workingDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        if (workingDir != null) pb.directory(workingDir);
        streamProcess(pb.start(), logFile, Arrays.toString(cmd));
    }

    private void runOnServer(BuildServer s, String rawCommand, Path logFile) throws IOException, InterruptedException {
        String[] cmd = platformService.buildCommand(s, rawCommand);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        streamProcess(pb.start(), logFile, rawCommand);
    }

    private void streamProcess(Process process, Path logFile, String label) throws IOException, InterruptedException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedWriter writer = Files.newBufferedWriter(logFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String line;
            while ((line = reader.readLine()) != null) { writer.write(line); writer.newLine(); writer.flush(); }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) throw new RuntimeException("Command failed (exit " + exitCode + "): " + label);
    }

    private void copyArtifactBack(BuildServer s, String remoteGlob, Path localArtifactDir, Path logFile) throws IOException, InterruptedException {
        writeLog(logFile, "\nCopying artifact back from build server...\n");
        String type = s.getType() != null ? s.getType().toUpperCase() : "LOCAL";
        switch (type) {
            case "SSH": {
                List<String> cmd = new ArrayList<>();
                cmd.add("scp"); cmd.add("-o"); cmd.add("StrictHostKeyChecking=no"); cmd.add("-o"); cmd.add("BatchMode=yes");
                if (s.getSshKeyPath() != null && !s.getSshKeyPath().isBlank()) { cmd.add("-i"); cmd.add(s.getSshKeyPath()); }
                cmd.add("-P"); cmd.add(String.valueOf(s.getPort() > 0 ? s.getPort() : 22));
                cmd.add(s.getUsername().trim() + "@" + s.getHost().trim() + ":\"" + remoteGlob + "\"");
                cmd.add(localArtifactDir.toString() + "/");
                runLocalCommand(cmd.toArray(new String[0]), logFile, null);
                break;
            }
            case "DOCKER":
                runLocalCommand(new String[]{"docker", "cp", s.getContainerOrPod().trim() + ":" + remoteGlob.replace("*", ""), localArtifactDir.toString()}, logFile, null);
                break;
            case "KUBERNETES": {
                String src = (s.getNamespace() != null && !s.getNamespace().isBlank() ? s.getNamespace() + "/" : "")
                        + s.getContainerOrPod().trim() + ":" + remoteGlob.replace("*", "");
                runLocalCommand(new String[]{"kubectl", "cp", src, localArtifactDir.toString()}, logFile, null);
                break;
            }
            default:
                writeLog(logFile, "Copy-back not supported for type: " + type + "\n");
        }
    }

    private void createBaseDirectories() throws IOException {
        Files.createDirectories(workspaceRoot);
        Files.createDirectories(logsRoot);
        Files.createDirectories(artifactsRoot);
    }

    private void writeLog(Path logFile, String message) throws IOException {
        Files.writeString(logFile, message, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void cleanDirectory(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
    }

    private Path findJarFile(Path workspace) throws IOException {
        Path targetDir = workspace.resolve("target");
        if (!Files.exists(targetDir)) return null;
        try (Stream<Path> files = Files.walk(targetDir)) {
            return files.filter(p -> p.toString().endsWith(".jar"))
                    .filter(p -> !p.toString().endsWith("sources.jar"))
                    .filter(p -> !p.toString().endsWith("javadoc.jar"))
                    .findFirst().orElse(null);
        }
    }

    public String readLogFile(String jobId) {
        Path logFile = logsRoot.resolve(jobId + ".log");
        try {
            if (!Files.exists(logFile)) return "Log file not created yet.";
            return Files.readString(logFile);
        } catch (IOException ex) {
            return "Unable to read log file: " + ex.getMessage();
        }
    }
}