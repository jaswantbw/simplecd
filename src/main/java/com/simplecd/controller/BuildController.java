package com.simplecd.controller;

import com.simplecd.model.BuildJob;
import com.simplecd.service.BuildService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class BuildController {

    private final BuildService buildService;

    public BuildController(BuildService buildService) {
        this.buildService = buildService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("jobs", buildService.getAllJobs());
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
}