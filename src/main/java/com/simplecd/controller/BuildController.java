package com.simplecd.controller;

import com.simplecd.model.BuildJob;
import com.simplecd.model.BuildStatus;
import com.simplecd.service.BuildService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@Controller
public class BuildController {

    private final BuildService buildService;

    public BuildController(BuildService buildService) {
        this.buildService = buildService;
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
    public String cloneRepository(@RequestParam String repoUrl, @RequestParam String pat) {
        try {
            buildService.cloneRepository(repoUrl, pat);
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
}