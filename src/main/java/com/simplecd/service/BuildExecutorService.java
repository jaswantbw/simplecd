// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
package com.simplecd.service;

import com.simplecd.model.BuildJob;
import com.simplecd.model.BuildStatus;
import com.simplecd.model.Runner;
import com.simplecd.model.RunnerStatus;
import com.simplecd.model.RunnerType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

@Service
public class BuildExecutorService {

    private final Path workspaceRoot = Paths.get("C:/SimpleCD/workspaces");
    private final Path logsRoot      = Paths.get("C:/SimpleCD/logs");
    private final Path artifactsRoot = Paths.get("C:/SimpleCD/artifacts");

    private final RunnerService runnerService;

    public BuildExecutorService(RunnerService runnerService) {
        this.runnerService = runnerService;
    }

    @Async
    public void executeBuild(BuildJob job) {

        Path workspace  = workspaceRoot.resolve(job.getJobId());
        Path logFile    = logsRoot.resolve(job.getJobId() + ".log");
        Path artifactDir = artifactsRoot.resolve(job.getJobId());

        job.setWorkspacePath(workspace.toString());
        job.setLogPath(logFile.toString());
        job.setArtifactPath(artifactDir.toString());

        // Resolve the runner that will execute this build
        Runner runner = resolveRunner(job);

        try {
            createBaseDirectories();
            cleanDirectory(workspace);
            Files.createDirectories(workspace);
            Files.createDirectories(artifactDir);

            job.setStatus(BuildStatus.RUNNING);
            if (runner != null) {
                runner.setStatus(RunnerStatus.BUSY);
            }

            writeLog(logFile, "SimpleCD Build Started\n");
            writeLog(logFile, "Job ID    : " + job.getJobId() + "\n");
            writeLog(logFile, "Repository: " + job.getRepoUrl() + "\n");
            writeLog(logFile, "Branch    : " + job.getBranch() + "\n");
            writeLog(logFile, "Runner    : " + (runner == null ? "LOCAL (default)" : runner.getName()
                    + " [" + runner.getType() + "/" + runner.getShell() + "]") + "\n\n");

            // ---- Step 1: clone ----
            if (runner == null || runner.getType() == RunnerType.LOCAL) {
                // Direct ProcessBuilder clone — workspace is local
                runLocalCommand(
                        new String[]{"git", "clone", "-b", job.getBranch(), job.getRepoUrl(), workspace.toString()},
                        logFile, null);

                // ---- Step 2: build ----
                runLocalCommand(
                        new String[]{"mvn", "clean", "package"},
                        logFile, workspace.toFile());
            } else {
                // Remote runner: workspace lives on the runner; use configured shell
                String remoteWork = runner.getWorkDir() != null && !runner.getWorkDir().isBlank()
                        ? runner.getWorkDir() + "/" + job.getJobId()
                        : "/tmp/simplecd/" + job.getJobId();

                // mkdir + clone on the runner
                runOnRunner(runner, "mkdir -p " + remoteWork, logFile);
                runOnRunner(runner, "git clone -b " + shellQuote(job.getBranch())
                        + " " + shellQuote(job.getRepoUrl()) + " " + remoteWork, logFile);

                // build on the runner
                runOnRunner(runner, "cd " + remoteWork + " && mvn clean package", logFile);

                // copy back artifact over SSH for SSH runners (scp); for DOCKER/K8S docker cp / kubectl cp
                String remoteJar = remoteWork + "/target/*.jar";
                copyArtifactBack(runner, remoteJar, artifactDir, logFile, job.getJobId());
            }

            // ---- Step 3: archive artifact (local runner) ----
            if (runner == null || runner.getType() == RunnerType.LOCAL) {
                Path jarFile = findJarFile(workspace);
                if (jarFile == null) {
                    throw new RuntimeException("No JAR artifact found in target/ folder.");
                }
                Path copiedArtifact = artifactDir.resolve(jarFile.getFileName());
                Files.copy(jarFile, copiedArtifact, StandardCopyOption.REPLACE_EXISTING);
                writeLog(logFile, "\nArtifact saved: " + copiedArtifact + "\n");
                job.setArtifactPath(copiedArtifact.toString());
            }

            job.setStatus(BuildStatus.SUCCESS);
            writeLog(logFile, "\n✔ BUILD SUCCESS\n");

        } catch (Exception ex) {
            job.setStatus(BuildStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            try { writeLog(logFile, "\n✘ BUILD FAILED: " + ex.getMessage() + "\n"); } catch (IOException ignored) {}

        } finally {
            job.setEndTime(LocalDateTime.now());
            if (runner != null && runner.getStatus() == RunnerStatus.BUSY) {
                runner.setStatus(RunnerStatus.ONLINE);
            }
        }
    }

    // ---- Runner resolution ----

    private Runner resolveRunner(BuildJob job) {
        if (job.getRunnerId() == null || job.getRunnerId().isBlank()) {
            return null; // use local fallback
        }
        return runnerService.getById(job.getRunnerId());
    }

    // ---- Command execution ----

    /** Runs a command array directly via ProcessBuilder (local runner or default). */
    private void runLocalCommand(String[] command, Path logFile, File workingDir)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        if (workingDir != null) pb.directory(workingDir);
        streamProcess(pb.start(), logFile, Arrays.toString(command));
    }

    /** Wraps rawCommand for a remote runner and executes it via ProcessBuilder. */
    private void runOnRunner(Runner runner, String rawCommand, Path logFile)
            throws IOException, InterruptedException {
        String[] cmd = runnerService.buildCommand(runner, rawCommand);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        streamProcess(pb.start(), logFile, rawCommand);
    }

    private void streamProcess(Process process, Path logFile, String label)
            throws IOException, InterruptedException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedWriter writer = Files.newBufferedWriter(logFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed (exit " + exitCode + "): " + label);
        }
    }

    // ---- Artifact copy-back for remote runners ----

    private void copyArtifactBack(Runner runner, String remoteGlob, Path localArtifactDir,
                                   Path logFile, String jobId) throws IOException, InterruptedException {
        writeLog(logFile, "\nCopying artifact back from runner...\n");
        switch (runner.getType()) {
            case SSH -> {
                // scp user@host:"remoteGlob" localArtifactDir/
                String scpSrc = runner.getUsername().trim() + "@" + runner.getHost().trim() + ":\"" + remoteGlob + "\"";
                String[] scpCmd = buildScpCommand(runner, scpSrc, localArtifactDir.toString() + "/");
                runLocalCommand(scpCmd, logFile, null);
            }
            case DOCKER -> {
                // docker cp <container>:remoteGlob localArtifactDir
                String[] cpCmd = {"docker", "cp",
                        runner.getContainerOrPod().trim() + ":" + remoteGlob.replace("*", ""),
                        localArtifactDir.toString()};
                runLocalCommand(cpCmd, logFile, null);
            }
            case KUBERNETES -> {
                // kubectl cp <ns>/<pod>:remotePath localArtifactDir
                String src = (runner.getNamespace() != null && !runner.getNamespace().isBlank()
                        ? runner.getNamespace().trim() + "/" : "")
                        + runner.getContainerOrPod().trim() + ":" + remoteGlob.replace("*", "");
                String[] cpCmd = {"kubectl", "cp", src, localArtifactDir.toString()};
                runLocalCommand(cpCmd, logFile, null);
            }
            default -> writeLog(logFile, "Artifact copy-back not supported for type: " + runner.getType() + "\n");
        }
    }

    private String[] buildScpCommand(Runner runner, String src, String dest) {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("scp");
        cmd.add("-o"); cmd.add("StrictHostKeyChecking=no");
        cmd.add("-o"); cmd.add("BatchMode=yes");
        if (runner.getSshKeyPath() != null && !runner.getSshKeyPath().isBlank()) {
            cmd.add("-i"); cmd.add(runner.getSshKeyPath().trim());
        }
        cmd.add("-P"); cmd.add(String.valueOf(runner.getPort() > 0 ? runner.getPort() : 22));
        cmd.add(src);
        cmd.add(dest);
        return cmd.toArray(new String[0]);
    }

    // ---- Utilities ----

    private String shellQuote(String s) {
        return "\"" + s.replace("\"", "\\\"") + "\"";
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
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }

    private Path findJarFile(Path workspace) throws IOException {
        Path targetDir = workspace.resolve("target");
        if (!Files.exists(targetDir)) return null;
        try (Stream<Path> files = Files.walk(targetDir)) {
            return files
                    .filter(p -> p.toString().endsWith(".jar"))
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

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.stream.Stream;

@Service
public class BuildExecutorService {

    private final Path workspaceRoot = Paths.get("C:/SimpleCD/workspaces");
    private final Path logsRoot = Paths.get("C:/SimpleCD/logs");
    private final Path artifactsRoot = Paths.get("C:/SimpleCD/artifacts");

    @Async
    public void executeBuild(BuildJob job) {

        Path workspace = workspaceRoot.resolve(job.getJobId());
        Path logFile = logsRoot.resolve(job.getJobId() + ".log");
        Path artifactDir = artifactsRoot.resolve(job.getJobId());

        job.setWorkspacePath(workspace.toString());
        job.setLogPath(logFile.toString());
        job.setArtifactPath(artifactDir.toString());

        try {
            createBaseDirectories();

            cleanDirectory(workspace);
            Files.createDirectories(workspace);
            Files.createDirectories(artifactDir);

            job.setStatus(BuildStatus.RUNNING);

            writeLog(logFile, "SimpleCD Build Started\n");
            writeLog(logFile, "Job ID: " + job.getJobId() + "\n");
            writeLog(logFile, "Repository: " + job.getRepoUrl() + "\n");
            writeLog(logFile, "Branch: " + job.getBranch() + "\n\n");

            runCommand(
                    new String[]{"git", "clone", "-b", job.getBranch(), job.getRepoUrl(), workspace.toString()},
                    logFile,
                    null
            );

            runCommand(
                    new String[]{"mvn", "clean", "package"},
                    logFile,
                    workspace.toFile()
            );

            Path jarFile = findJarFile(workspace);

            if (jarFile == null) {
                throw new RuntimeException("No JAR artifact found in target folder.");
            }

            Path copiedArtifact = artifactDir.resolve(jarFile.getFileName());
            Files.copy(jarFile, copiedArtifact, StandardCopyOption.REPLACE_EXISTING);

            writeLog(logFile, "\nArtifact saved: " + copiedArtifact + "\n");

            job.setArtifactPath(copiedArtifact.toString());
            job.setStatus(BuildStatus.SUCCESS);

        } catch (Exception ex) {
            job.setStatus(BuildStatus.FAILED);
            job.setErrorMessage(ex.getMessage());

            try {
                writeLog(logFile, "\nBUILD FAILED: " + ex.getMessage() + "\n");
            } catch (IOException ignored) {
            }

        } finally {
            job.setEndTime(LocalDateTime.now());
        }
    }

    private void createBaseDirectories() throws IOException {
        Files.createDirectories(workspaceRoot);
        Files.createDirectories(logsRoot);
        Files.createDirectories(artifactsRoot);
    }

    private void runCommand(String[] command, Path logFile, File workingDir) throws IOException, InterruptedException {

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        if (workingDir != null) {
            processBuilder.directory(workingDir);
        }

        Process process = processBuilder.start();

        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedWriter writer = Files.newBufferedWriter(logFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        ) {
            String line;

            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", command));
        }
    }

    private Path findJarFile(Path workspace) throws IOException {
        Path targetDir = workspace.resolve("target");

        if (!Files.exists(targetDir)) {
            return null;
        }

        try (Stream<Path> files = Files.walk(targetDir)) {
            return files
                    .filter(path -> path.toString().endsWith(".jar"))
                    .filter(path -> !path.toString().endsWith("sources.jar"))
                    .filter(path -> !path.toString().endsWith("javadoc.jar"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private void writeLog(Path logFile, String message) throws IOException {
        Files.writeString(
                logFile,
                message,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private void cleanDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    public String readLogFile(String jobId) {
        Path logFile = logsRoot.resolve(jobId + ".log");

        try {
            if (!Files.exists(logFile)) {
                return "Log file not created yet.";
            }

            return Files.readString(logFile);

        } catch (IOException ex) {
            return "Unable to read log file: " + ex.getMessage();
        }
    }
}