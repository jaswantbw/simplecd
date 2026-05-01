// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
package com.simplecd.controller;
import com.simplecd.model.Runner; import com.simplecd.model.RunnerShell; import com.simplecd.model.RunnerType;
import com.simplecd.service.RunnerService;
import org.springframework.http.ResponseEntity; import org.springframework.web.bind.annotation.*;
import java.util.Collection; import java.util.Map;
@RestController
@RequestMapping("/api/runners")
public class RunnerController {
    private final RunnerService runnerService;
    public RunnerController(RunnerService runnerService){this.runnerService=runnerService;}
    @GetMapping
    public Collection<Runner> list(){return runnerService.getAll();}
    @PostMapping
    public ResponseEntity<Runner> register(@RequestBody RunnerRequest req){return ResponseEntity.ok(runnerService.register(map(new Runner(),req)));}
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String,String>> delete(@PathVariable String id){
        boolean removed=runnerService.delete(id);
        if(removed)return ResponseEntity.ok(Map.of("status","deleted"));
        return ResponseEntity.notFound().build();
    }
    @PostMapping("/{id}/ping")
    public ResponseEntity<Map<String,String>> ping(@PathVariable String id){
        Runner runner=runnerService.getById(id); if(runner==null)return ResponseEntity.notFound().build();
        String result=runnerService.ping(id);
        return ResponseEntity.ok(Map.of("result",result,"status",runner.getStatus().name()));
    }
    @PutMapping("/{id}")
    public ResponseEntity<Runner> update(@PathVariable String id,@RequestBody RunnerRequest req){
        Runner runner=runnerService.getById(id); if(runner==null)return ResponseEntity.notFound().build();
        return ResponseEntity.ok(runnerService.update(map(runner,req)));
    }
    private Runner map(Runner r,RunnerRequest req){
        r.setName(req.name()); r.setType(RunnerType.valueOf(req.type().toUpperCase())); r.setShell(RunnerShell.valueOf(req.shell().toUpperCase()));
        r.setHost(req.host()); r.setPort(req.port()>0?req.port():22); r.setUsername(req.username()); r.setSshKeyPath(req.sshKeyPath());
        r.setContainerOrPod(req.containerOrPod()); r.setNamespace(req.namespace()); r.setWorkDir(req.workDir()); r.setLabels(req.labels());
        return r;
    }
    record RunnerRequest(String name,String type,String shell,String host,int port,String username,String sshKeyPath,String containerOrPod,String namespace,String workDir,String labels){}
}