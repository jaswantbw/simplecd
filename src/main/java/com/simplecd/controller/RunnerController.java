// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
package com.simplecd.controller;

import com.simplecd.model.Runner;
import com.simplecd.model.RunnerShell;
import com.simplecd.model.RunnerType;
import com.simplecd.service.RunnerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

@RestController
@RequestMapping("/api/runners")
public class RunnerController {

    private final RunnerService runnerService;

    public RunnerController(RunnerService runnerService) {
        this.runnerService = runnerService;
    }

    /** List all registered runners. */
    @GetMapping
    public Collection<Runner> list() {
        return runnerService.getAll();
    }

    /** Register a new runner. */
    @PostMapping
    public ResponseEntity<Runner> register(@RequestBody RunnerRequest req) {
        Runner runner = new Runner();
        runner.setName(req.name());
        runner.setType(RunnerType.valueOf(req.type().toUpperCase()));
        runner.setShell(RunnerShell.valueOf(req.shell().toUpperCase()));
        runner.setHost(req.host());
        runner.setPort(req.port() > 0 ? req.port() : 22);
        runner.setUsername(req.username());
        runner.setSshKeyPath(req.sshKeyPath());
        runner.setContainerOrPod(req.containerOrPod());
        runner.setNamespace(req.namespace());
        runner.setWorkDir(req.workDir());
        runner.setLabels(req.labels());
        return ResponseEntity.ok(runnerService.register(runner));
    }

    /** Delete a runner by ID. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String id) {
        boolean removed = runnerService.delete(id);
        if (removed) return ResponseEntity.ok(Map.of("status", "deleted"));
        return ResponseEntity.notFound().build();
    }

    /** Ping a runner to check its status. */
    @PostMapping("/{id}/ping")
    public ResponseEntity<Map<String, String>> ping(@PathVariable String id) {
        Runner runner = runnerService.getById(id);
        if (runner == null) return ResponseEntity.notFound().build();
        String result = runnerService.ping(id);
        return ResponseEntity.ok(Map.of("result", result, "status", runner.getStatus().name()));
    }

    /** Update runner configuration. */
    @PutMapping("/{id}")
    public ResponseEntity<Runner> update(@PathVariable String id, @RequestBody RunnerRequest req) {
        Runner runner = runnerService.getById(id);
        if (runner == null) return ResponseEntity.notFound().build();
        runner.setName(req.name());
        runner.setType(RunnerType.valueOf(req.type().toUpperCase()));
        runner.setShell(RunnerShell.valueOf(req.shell().toUpperCase()));
        runner.setHost(req.host());
        runner.setPort(req.port() > 0 ? req.port() : 22);
        runner.setUsername(req.username());
        runner.setSshKeyPath(req.sshKeyPath());
        runner.setContainerOrPod(req.containerOrPod());
        runner.setNamespace(req.namespace());
        runner.setWorkDir(req.workDir());
        runner.setLabels(req.labels());
        return ResponseEntity.ok(runnerService.update(runner));
    }

    // ---- request DTO ----

    record RunnerRequest(
            String name,
            String type,
            String shell,
            String host,
            int port,
            String username,
            String sshKeyPath,
            String containerOrPod,
            String namespace,
            String workDir,
            String labels
    ) {}
}
