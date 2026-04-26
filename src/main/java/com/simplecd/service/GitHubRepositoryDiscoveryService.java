package com.simplecd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplecd.model.GitProviderSettings;
import com.simplecd.model.RemoteRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class GitHubRepositoryDiscoveryService {

    private final GitProviderSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GitHubRepositoryDiscoveryService(GitProviderSettingsService settingsService) {
        this.settingsService = settingsService;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<RemoteRepository> fetchRepositories() throws Exception {
        GitProviderSettings settings = settingsService.getSettings();

        String pat = safe(settings.getPat());
        String username = safe(settings.getUsername());

        if (pat.isEmpty() && username.isEmpty()) {
            throw new IllegalArgumentException("Save Git settings first (username or PAT is required).");
        }

        String endpoint;
        if (!pat.isEmpty()) {
            endpoint = "https://api.github.com/user/repos?per_page=100&sort=updated";
        } else {
            endpoint = "https://api.github.com/users/" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                    + "/repos?per_page=100&sort=updated";
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET();

        if (!pat.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + pat);
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            String body = response.body() == null ? "" : response.body();
            throw new IOException("GitHub API error " + response.statusCode() + ": " + body);
        }

        JsonNode root = objectMapper.readTree(response.body());
        if (!root.isArray()) {
            throw new IOException("Unexpected GitHub API response.");
        }

        List<RemoteRepository> repositories = new ArrayList<>();
        for (JsonNode node : root) {
            repositories.add(new RemoteRepository(
                    node.path("name").asText(),
                    node.path("full_name").asText(),
                    node.path("clone_url").asText(),
                    node.path("default_branch").asText("main"),
                    node.path("private").asBoolean(false)
            ));
        }
        return repositories;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
