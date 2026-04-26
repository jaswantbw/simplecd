package com.simplecd.service;

import com.simplecd.model.BuildJob;
import com.simplecd.model.BuildStatus;
import com.simplecd.model.Repository;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BuildService {

    private final Map<String, BuildJob> jobs = new ConcurrentHashMap<>();
    private final BuildExecutorService buildExecutorService;
    private final RepositoryService repositoryService;
    private static final String REPOS_FOLDER = "code-repos";

    public BuildService(BuildExecutorService buildExecutorService, RepositoryService repositoryService) {
        this.buildExecutorService = buildExecutorService;
        this.repositoryService = repositoryService;
        initializeReposFolder();
    }

    private void initializeReposFolder() {
        try {
            Path reposPath = Paths.get(REPOS_FOLDER);
            if (!Files.exists(reposPath)) {
                Files.createDirectories(reposPath);
            }
        } catch (IOException e) {
            System.err.println("Error creating repos folder: " + e.getMessage());
        }
    }

    public void cloneRepository(String repoUrl, String pat) throws Exception {
        // Extract repo name from URL
        String repoName = repoUrl.substring(repoUrl.lastIndexOf("/") + 1).replace(".git", "");
        Path repoPath = Paths.get(REPOS_FOLDER, repoName);

        // Create auth URL with PAT
        String authUrl = repoUrl.replace("https://", "https://oauth2:" + pat + "@");

        // Git clone command
        ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth", "1", authUrl, repoPath.toString());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        StringBuilder output = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("Git clone failed: " + output.toString());
        }

        // Save repository to service after successful clone
        repositoryService.addRepository(repoName, repoUrl, repoPath.toString(), "main");
    }

    public BuildJob startBuild(String repoUrl, String branch) {

        String jobId = UUID.randomUUID().toString();

        BuildJob job = new BuildJob();
        job.setJobId(jobId);
        job.setRepoUrl(repoUrl);
        job.setBranch(branch);
        job.setStatus(BuildStatus.QUEUED);
        job.setStartTime(LocalDateTime.now());

        jobs.put(jobId, job);

        buildExecutorService.executeBuild(job);

        return job;
    }

    public Collection<BuildJob> getAllJobs() {
        return jobs.values();
    }

    public Collection<?> getAllRepositories() {
        return repositoryService.getAllRepositories();
    }

    public BuildJob getJob(String jobId) {
        return jobs.get(jobId);
    }

    public String readLogs(String jobId) {
        return buildExecutorService.readLogFile(jobId);
    }
}