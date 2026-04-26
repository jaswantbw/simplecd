package com.simplecd.controller;

import com.simplecd.model.BuildJob;
import com.simplecd.model.BuildStatus;
import com.simplecd.model.GitProviderSettings;
import com.simplecd.model.RemoteRepository;
import com.simplecd.service.BuildService;
import com.simplecd.service.GitHubRepositoryDiscoveryService;
import com.simplecd.service.GitProviderSettingsService;
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

    public BuildController(BuildService buildService,
                           GitProviderSettingsService gitProviderSettingsService,
                           GitHubRepositoryDiscoveryService gitHubRepositoryDiscoveryService) {
        this.buildService = buildService;
        this.gitProviderSettingsService = gitProviderSettingsService;
        this.gitHubRepositoryDiscoveryService = gitHubRepositoryDiscoveryService;
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
        return "index";
    }

    @PostMapping("/build")
    public String startBuild(@RequestParam String repoUrl,
                             @RequestParam String branch) {

        buildService.startBuild(repoUrl, branch);
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
    @GetMapping("/api/git-settings")
    public GitProviderSettings getGitSettings() {
        return gitProviderSettingsService.getSettings();
    }

    @ResponseBody
    @PostMapping("/api/git-settings")
    public GitProviderSettings saveGitSettings(@RequestParam(defaultValue = "") String providerUrl,
                                               @RequestParam(defaultValue = "") String username,
                                               @RequestParam(defaultValue = "") String password,
                                               @RequestParam(defaultValue = "") String pat,
                                               @RequestParam(defaultValue = "") String sshKey) {
        GitProviderSettings settings = new GitProviderSettings();
        settings.setProviderUrl(providerUrl);
        settings.setUsername(username);
        settings.setPassword(password);
        settings.setPat(pat);
        settings.setSshKey(sshKey);
        return gitProviderSettingsService.saveSettings(settings);
    }

    @ResponseBody
    @GetMapping("/api/github-repos")
    public List<RemoteRepository> getGitHubRepositories() throws Exception {
        return gitHubRepositoryDiscoveryService.fetchRepositories();
    }
}