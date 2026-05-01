package com.simplecd.controller;

import com.simplecd.model.BuildServer;
import com.simplecd.model.BuildServerGroup;
import com.simplecd.service.BuildPlatformService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/build-platform")
public class BuildServerController {

    private final BuildPlatformService svc;

    public BuildServerController(BuildPlatformService svc) { this.svc = svc; }

    // ── Groups ────────────────────────────────────────────────────────────────

    @GetMapping("/groups")
    public Collection<BuildServerGroup> listGroups() { return svc.getAllGroups(); }

    @PostMapping("/groups")
    public ResponseEntity<BuildServerGroup> createGroup(@RequestBody GroupRequest req) {
        if (req.name() == null || req.name().isBlank())
            return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(svc.createGroup(req.name().trim(), req.description() != null ? req.description().trim() : ""));
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Map<String, String>> deleteGroup(@PathVariable String id) {
        return svc.deleteGroup(id)
                ? ResponseEntity.ok(Map.of("status", "deleted"))
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/groups/{id}/regenerate-token")
    public ResponseEntity<Map<String, String>> regenerateToken(@PathVariable String id) {
        String token = svc.regenerateToken(id);
        return token != null
                ? ResponseEntity.ok(Map.of("token", token))
                : ResponseEntity.notFound().build();
    }

    // ── Servers ───────────────────────────────────────────────────────────────

    @GetMapping("/groups/{groupId}/servers")
    public List<BuildServer> listServers(@PathVariable String groupId) {
        return svc.getServersByGroup(groupId);
    }

    @PostMapping("/groups/{groupId}/servers")
    public ResponseEntity<BuildServer> addServer(@PathVariable String groupId, @RequestBody BuildServer s) {
        s.setGroupId(groupId);
        return ResponseEntity.ok(svc.addServer(s));
    }

    @DeleteMapping("/servers/{id}")
    public ResponseEntity<Map<String, String>> deleteServer(@PathVariable String id) {
        return svc.deleteServer(id)
                ? ResponseEntity.ok(Map.of("status", "deleted"))
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/servers/{id}/ping")
    public ResponseEntity<Map<String, String>> ping(@PathVariable String id) {
        BuildServer s = svc.getServerById(id);
        if (s == null) return ResponseEntity.notFound().build();
        String result = svc.ping(id);
        return ResponseEntity.ok(Map.of("result", result, "status", s.getStatus() != null ? s.getStatus().name() : "UNKNOWN"));
    }

    // ── Self-registration (auth via Bearer PAT, group by groupId in body) ─────

    @PostMapping("/register")
    public ResponseEntity<?> selfRegister(@RequestBody RegisterRequest req) {
        if (req.groupId() == null || req.groupId().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "groupId is required"));
        try {
            BuildServer s = svc.registerToGroup(
                    req.groupId(), req.agentName(), req.os(),
                    req.type(), req.shell(), req.host(),
                    req.port(), req.username(), req.sshKeyPath(),
                    req.containerOrPod(), req.namespace(), req.workDir()
            );
            return ResponseEntity.ok(Map.of(
                    "status", "registered",
                    "id", s.getId(),
                    "agentName", s.getAgentName(),
                    "groupName", s.getGroupName() != null ? s.getGroupName() : ""
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(400).body(Map.of("error", ex.getMessage()));
        }
    }

    // ── Records ───────────────────────────────────────────────────────────────

    record GroupRequest(String name, String description) {}

    record RegisterRequest(String groupId, String agentName, String os, String type, String shell,
                           String host, int port, String username, String sshKeyPath,
                           String containerOrPod, String namespace, String workDir) {}
}