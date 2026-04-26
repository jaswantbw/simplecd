package com.simplecd.service;

import com.simplecd.model.BuildJob;
import com.simplecd.model.BuildStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BuildService {

    private final Map<String, BuildJob> jobs = new ConcurrentHashMap<>();
    private final BuildExecutorService buildExecutorService;

    public BuildService(BuildExecutorService buildExecutorService) {
        this.buildExecutorService = buildExecutorService;
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

    public BuildJob getJob(String jobId) {
        return jobs.get(jobId);
    }

    public String readLogs(String jobId) {
        return buildExecutorService.readLogFile(jobId);
    }
}