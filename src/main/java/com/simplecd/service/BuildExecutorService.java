package com.simplecd.service;

import com.simplecd.model.BuildJob;
import com.simplecd.model.BuildStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
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