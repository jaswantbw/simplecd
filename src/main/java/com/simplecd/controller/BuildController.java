// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
package com.simplecd.controller;

import com.simplecd.model.BuildJob;
import com.simplecd.model.BuildStatus;
import com.simplecd.model.GitProviderProfile;
import com.simplecd.model.GitProviderSettings;
import com.simplecd.model.ProviderVerificationResult;
import com.simplecd.model.RemoteRepository;
import com.simplecd.service.BuildService;
import com.simplecd.service.GitHubRepositoryDiscoveryService;
import com.simplecd.service.GitProviderSettingsService;
import com.simplecd.service.RunnerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@Controller
public class BuildController {

    private final BuildService buildService;
    private final GitProviderSettingsService gitProviderSettingsService;
    private final GitHubRepositoryDiscoveryService gitHubRepositoryDiscoveryService;
    private final RunnerService runnerService;

    public BuildController(BuildService buildService,
                           GitProviderSettingsService gitProviderSettingsService,
                           GitHubRepositoryDiscoveryService gitHubRepositoryDiscoveryService,
                           RunnerService runnerService) {
        this.buildService = buildService;
        this.gitProviderSettingsService = gitProviderSettingsService;
        this.gitHubRepositoryDiscoveryService = gitHubRepositoryDiscoveryService;
        this.runnerService = runnerService;
    }

    @GetMapping("/")
    public String index(Model model) {
        Collection<BuildJob> jobs = buildService.getAllJobs();
        model.addAttribute("jobs", jobs);
        model.addAttribute("countQueued",  jobs.stream().filter(j -> j.getStatus() == BuildStatus.QUEUED).count());
        model.addAttribute("countSuccess", jobs.stream().filter(j -> j.getStatus() == BuildStatus.SUCCESS).count());
        model.addAttribute("countRunning", jobs.stream().filter(j -> j.getStatus() == BuildStatus.RUNNING).count());
        model.addAttribute("countFailed",  jobs.stream().filter(j -> j.getStatus() == BuildStatus.FAILED).count());
        model.addAttribute("successRate",  jobs.isEmpty() ? "—" : (jobs.stream().filter(j -> j.getStatus() == BuildStatus.SUCCESS).count() * 100 / jobs.size()) + "%");
        model.addAttribute("hasArtifacts", jobs.stream().anyMatch(j -> j.getArtifactPath() != null));
        model.addAttribute("runners", runnerService.getAll());
        return "index";
    }

    @PostMapping("/build")
    public String startBuild(@RequestParam String repoUrl,
                             @RequestParam String branch,
                             @RequestParam(required = false, defaultValue = "") String runnerId) {

        buildService.startBuild(repoUrl, branch, runnerId.isBlank() ? null : runnerId);
        return "redirect:/";
    }

    @GetMapping("/logs/{jobId}")
    public String logs(@PathVariable String jobId, Model model) {
        BuildJob job = buildService.getJob(jobId);
        model.addAttribute("job", job);
        return "logs";
    }

    @ResponseBody
    @GetMapping("/api/logs/{jobId}")
    public String getLogs(@PathVariable String jobId) {
        return buildService.readLogs(jobId);
    }

    @ResponseBody
    @PostMapping("/api/clone-repo")
    public String cloneRepository(@RequestParam String repoUrl,
                                  @RequestParam(required = false, defaultValue = "") String pat,
                                  @RequestParam(required = false, defaultValue = "main") String defaultBranch) {
        try {
            buildService.cloneRepository(repoUrl, pat, defaultBranch);
            return "Repository cloned successfully";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @ResponseBody
    @GetMapping("/api/repositories")
    public Collection<?> getRepositories() {
        return buildService.getAllRepositories();
    }

    @ResponseBody
    @PostMapping("/api/repositories/{repositoryId}/clone")
    public String cloneRepositoryById(@PathVariable String repositoryId,
                                      @RequestParam(required = false, defaultValue = "main") String branch) {
        try {
            return buildService.cloneRepositoryById(repositoryId, branch);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @ResponseBody
    @PostMapping("/api/repositories/{repositoryId}/pull")
    public String pullRepository(@PathVariable String repositoryId,
                                 @RequestParam(required = false, defaultValue = "main") String branch) {
        try {
            return buildService.pullRepository(repositoryId, branch);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @ResponseBody
    @PostMapping("/api/repositories/{repositoryId}/fetch")
    public String fetchRepository(@PathVariable String repositoryId) {
        try {
            return buildService.fetchRepository(repositoryId);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @ResponseBody
    @PostMapping("/api/repositories/{repositoryId}/checkout")
    public String checkoutRepository(@PathVariable String repositoryId,
                                     @RequestParam(required = false, defaultValue = "main") String branch) {
        try {
            return buildService.checkoutRepository(repositoryId, branch);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @ResponseBody
    @GetMapping("/api/repositories/{repositoryId}/branches")
    public List<String> getRepositoryBranches(@PathVariable String repositoryId) {
        try {
            return buildService.getRepositoryBranches(repositoryId);
        } catch (Exception e) {
            return List.of();
        }
    }

    @ResponseBody
    @GetMapping("/api/git-settings")
    public GitProviderSettings getGitSettings() {
        return gitProviderSettingsService.getSettings();
    }

    @ResponseBody
    @PostMapping("/api/git-settings")
    public GitProviderSettings saveGitSettings(@RequestBody GitProviderSettings settings) {
        return gitProviderSettingsService.saveSettings(settings);
    }

    @ResponseBody
    @PostMapping("/api/git-settings/verify")
    public ProviderVerificationResult verifyGitProvider(@RequestBody GitProviderProfile profile) {
        return gitHubRepositoryDiscoveryService.verifyProvider(profile);
    }

    @ResponseBody
    @GetMapping("/api/remote-repositories")
    public List<RemoteRepository> getRemoteRepositories(@RequestParam(required = false) String providerId) throws Exception {
        return gitHubRepositoryDiscoveryService.fetchRepositories(providerId);
    }

    @ResponseBody
    @GetMapping("/api/azure/collections")
    public List<String> getAzureCollections(@RequestParam String providerId) throws Exception {
        return gitHubRepositoryDiscoveryService.fetchAzureCollections(providerId);
    }

    @ResponseBody
    @GetMapping("/api/github/owners")
    public List<String> getGitHubOwners(@RequestParam String providerId) throws Exception {
        return gitHubRepositoryDiscoveryService.fetchGitHubOwners(providerId);
    }

    @ResponseBody
    @GetMapping("/api/github/repositories")
    public List<RemoteRepository> getGitHubRepositories(@RequestParam String providerId,
                                                        @RequestParam(required = false, defaultValue = "") String owner) throws Exception {
        return gitHubRepositoryDiscoveryService.fetchGitHubRepositories(providerId, owner);
    }

    @ResponseBody
    @GetMapping("/api/gitlab/groups")
    public List<String> getGitLabGroups(@RequestParam String providerId) throws Exception {
        return gitHubRepositoryDiscoveryService.fetchGitLabGroups(providerId);
    }

    @ResponseBody
    @GetMapping("/api/gitlab/projects")
    public List<RemoteRepository> getGitLabProjects(@RequestParam String providerId,
                                                    @RequestParam(required = false, defaultValue = "") String group) throws Exception {
        return gitHubRepositoryDiscoveryService.fetchGitLabProjects(providerId, group);
    }

    @ResponseBody
    @GetMapping("/api/azure/projects")
    public List<String> getAzureProjects(@RequestParam String providerId,
                                         @RequestParam(required = false, defaultValue = "") String collection) throws Exception {
        return gitHubRepositoryDiscoveryService.fetchAzureProjects(providerId, collection);
    }

    @ResponseBody
    @GetMapping("/api/azure/repositories")
    public List<RemoteRepository> getAzureRepositories(@RequestParam String providerId,
                                                       @RequestParam(required = false, defaultValue = "") String collection,
                                                       @RequestParam String project) throws Exception {
        return gitHubRepositoryDiscoveryService.fetchAzureRepositories(providerId, collection, project);
    }
}