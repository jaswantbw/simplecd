package com.simplecd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplecd.model.GitProviderProfile;
import com.simplecd.model.GitProviderSettings;
import com.simplecd.model.ProviderVerificationResult;
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
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        return fetchRepositories(null);
    }

    public List<RemoteRepository> fetchRepositories(String providerId) throws Exception {
        List<RemoteRepository> repositories = new ArrayList<>();
        GitProviderSettings settings = settingsService.getSettings();
        for (GitProviderProfile profile : settings.getProviders()) {
            if (providerId != null && !providerId.isBlank() && !safe(providerId).equals(safe(profile.getId()))) {
                continue;
            }

            String providerType = safe(profile.getProviderType()).toLowerCase();
            if (providerType.isBlank()) {
                providerType = settingsService.inferProviderType(profile.getProviderUrl());
            }

            try {
                switch (providerType) {
                    case "github" -> repositories.addAll(fetchGitHubRepositories(profile));
                    case "gitlab" -> repositories.addAll(fetchGitLabRepositories(profile));
                    case "azure" -> repositories.addAll(fetchAzureRepositories(profile));
                    case "bitbucket" -> repositories.addAll(fetchBitbucketRepositories(profile));
                    default -> {
                    }
                }
            } catch (Exception ex) {
                // Skip failing provider and keep others discoverable in the UI.
                System.err.println("Repository discovery failed for provider '" + safe(profile.getName()) + "' ("
                        + providerType + "): " + ex.getMessage());
            }
        }
        return repositories;
    }

    public ProviderVerificationResult verifyProvider(GitProviderProfile profile) {
        String providerType = safe(profile == null ? "" : profile.getProviderType()).toLowerCase();
        if (providerType.isBlank()) {
            providerType = settingsService.inferProviderType(profile == null ? "" : profile.getProviderUrl());
        }

        try {
            return switch (providerType) {
                case "github" -> verifyGitHubProvider(profile);
                case "gitlab" -> verifyGitLabProvider(profile);
                case "azure" -> verifyAzureProvider(profile);
                case "bitbucket" -> verifyBitbucketProvider(profile);
                default -> new ProviderVerificationResult(false, 400, "Unsupported provider type.", providerType);
            };
        } catch (Exception ex) {
            return new ProviderVerificationResult(false, 500, ex.getMessage(), providerType);
        }
    }

    public List<String> fetchGitHubOwners(String providerId) throws Exception {
        GitProviderProfile profile = findProviderProfile(providerId);
        if (profile == null) {
            return List.of();
        }

        Set<String> owners = new LinkedHashSet<>();
        String inferredOwner = extractFirstPathSegment(safe(profile.getProviderUrl()));
        if (!inferredOwner.isBlank()) {
            owners.add(inferredOwner);
        }

        String pat = safe(profile.getPat());
        if (!pat.isBlank()) {
            String baseApi = buildGitHubApiBaseUrl(profile);
            HttpRequest meRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseApi + "/user"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + pat)
                    .GET()
                    .build();
            JsonNode me = readJson(meRequest, "GitHub API error ");
            String login = me.path("login").asText();
            if (!login.isBlank()) {
                owners.add(login);
            }

            HttpRequest orgsRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseApi + "/user/orgs?per_page=100"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + pat)
                    .GET()
                    .build();
            JsonNode orgs = readJson(orgsRequest, "GitHub API error ");
            for (JsonNode org : orgs) {
                String loginName = org.path("login").asText();
                if (!loginName.isBlank()) {
                    owners.add(loginName);
                }
            }
        } else if (!safe(profile.getUsername()).isBlank()) {
            owners.add(safe(profile.getUsername()));
        }

        return new ArrayList<>(owners);
    }

    public List<RemoteRepository> fetchGitHubRepositories(String providerId, String owner) throws Exception {
        GitProviderProfile profile = findProviderProfile(providerId);
        if (profile == null) {
            return List.of();
        }

        String selectedOwner = safe(owner);
        if (selectedOwner.isBlank()) {
            selectedOwner = extractFirstPathSegment(safe(profile.getProviderUrl()));
        }
        if (selectedOwner.isBlank()) {
            return fetchGitHubRepositories(profile);
        }

        String pat = safe(profile.getPat());
        String baseApi = buildGitHubApiBaseUrl(profile);
        String endpoint;
        if (!pat.isBlank()) {
            endpoint = baseApi + "/user/repos?per_page=100&sort=updated&affiliation=owner,organization_member";
        } else {
            endpoint = baseApi + "/users/" + encode(selectedOwner) + "/repos?per_page=100&sort=updated";
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .GET();
        if (!pat.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + pat);
        }

        JsonNode root = readJson(requestBuilder.build(), "GitHub API error ");
        List<RemoteRepository> repositories = new ArrayList<>();
        for (JsonNode node : root) {
            String fullName = node.path("full_name").asText();
            if (!selectedOwner.isBlank() && !fullName.toLowerCase().startsWith(selectedOwner.toLowerCase() + "/")) {
                continue;
            }
            repositories.add(new RemoteRepository(
                    node.path("name").asText(),
                    fullName,
                    node.path("clone_url").asText(),
                    node.path("default_branch").asText("main"),
                    node.path("private").asBoolean(false),
                    safe(profile.getId()),
                    safe(profile.getName()),
                    "github"
            ));
        }
        return repositories;
    }

    public List<String> fetchGitLabGroups(String providerId) throws Exception {
        GitProviderProfile profile = findProviderProfile(providerId);
        if (profile == null) {
            return List.of();
        }

        Set<String> groups = new LinkedHashSet<>();
        String inferredGroup = extractFullPathAfterHost(safe(profile.getProviderUrl()));
        if (!inferredGroup.isBlank()) {
            groups.add(inferredGroup);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildGitLabApiBaseUrl(profile) + "/groups?per_page=100&min_access_level=10"))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("PRIVATE-TOKEN", resolveSecret(profile))
                .GET()
                .build();
        JsonNode root = readJson(request, "GitLab API error ");
        for (JsonNode node : root) {
            String fullPath = node.path("full_path").asText();
            if (!fullPath.isBlank()) {
                groups.add(fullPath);
            }
        }
        return new ArrayList<>(groups);
    }

    public List<RemoteRepository> fetchGitLabProjects(String providerId, String groupPath) throws Exception {
        GitProviderProfile profile = findProviderProfile(providerId);
        if (profile == null) {
            return List.of();
        }

        String selectedGroup = safe(groupPath);
        if (selectedGroup.isBlank()) {
            selectedGroup = extractFullPathAfterHost(safe(profile.getProviderUrl()));
        }

        String endpoint;
        if (selectedGroup.isBlank()) {
            endpoint = buildGitLabApiBaseUrl(profile) + "/projects?membership=true&per_page=100";
        } else {
            endpoint = buildGitLabApiBaseUrl(profile) + "/groups/" + encode(selectedGroup) + "/projects?include_subgroups=true&per_page=100";
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("PRIVATE-TOKEN", resolveSecret(profile))
                .GET()
                .build();
        JsonNode root = readJson(request, "GitLab API error ");
        List<RemoteRepository> repositories = new ArrayList<>();
        for (JsonNode node : root) {
            String httpUrl = node.path("http_url_to_repo").asText();
            if (httpUrl.isBlank()) {
                continue;
            }
            repositories.add(new RemoteRepository(
                    node.path("name").asText(),
                    node.path("path_with_namespace").asText(node.path("name").asText()),
                    httpUrl,
                    node.path("default_branch").asText("main"),
                    node.path("visibility").asText("private").equalsIgnoreCase("private"),
                    safe(profile.getId()),
                    safe(profile.getName()),
                    "gitlab"
            ));
        }
        return repositories;
    }

    public List<String> fetchAzureCollections(String providerId) throws Exception {
        GitProviderProfile profile = findProviderProfile(providerId);
        if (profile == null) {
            return List.of();
        }

        String providerType = safe(profile.getProviderType()).toLowerCase();
        if (providerType.isBlank()) {
            providerType = settingsService.inferProviderType(profile.getProviderUrl());
        }
        if (!"azure".equals(providerType)) {
            return List.of();
        }

        URI providerUri = URI.create(safe(profile.getProviderUrl()));
        AzureBrowseContext context = resolveAzureBrowseContext(providerUri);
        Set<String> collections = new LinkedHashSet<>();
        if (!context.inferredCollection.isBlank()) {
            collections.add(context.inferredCollection);
        }

        if (context.isDevAzureHost) {
            if (!context.inferredCollection.isBlank()) {
                collections.add(context.inferredCollection);
            }
            return new ArrayList<>(collections);
        }

        try {
            String endpoint = context.baseHost;
            if (!context.pathPrefix.isBlank()) {
                endpoint += "/" + context.pathPrefix;
            }
            endpoint += "/_apis/projectcollections?api-version=7.1-preview.1";

            JsonNode root = readJson(withBasicAuth(endpoint, profile), "Azure DevOps API error ");
            for (JsonNode node : root.path("value")) {
                String collectionName = node.path("name").asText();
                if (!collectionName.isBlank()) {
                    collections.add(collectionName);
                }
            }
        } catch (Exception ignored) {
            // Some Azure DevOps Server installations disallow listing collections for PAT users.
            // In that case we fallback to collection inferred from provider URL.
        }

        return new ArrayList<>(collections);
    }

    public List<String> fetchAzureProjects(String providerId, String collectionName) throws Exception {
        GitProviderProfile profile = findProviderProfile(providerId);
        if (profile == null) {
            return List.of();
        }

        String providerType = safe(profile.getProviderType()).toLowerCase();
        if (providerType.isBlank()) {
            providerType = settingsService.inferProviderType(profile.getProviderUrl());
        }
        if (!"azure".equals(providerType)) {
            return List.of();
        }

        URI providerUri = URI.create(safe(profile.getProviderUrl()));
        AzureBrowseContext context = resolveAzureBrowseContext(providerUri);
        String selectedCollection = safe(collectionName);
        if (selectedCollection.isBlank()) {
            selectedCollection = context.inferredCollection;
        }

        List<String> names = new ArrayList<>();
        try {
            String projectsEndpoint = buildAzureCollectionBaseUrl(context, selectedCollection)
                    + "/_apis/projects?api-version=7.1-preview.4";
            JsonNode projects = readJson(withBasicAuth(projectsEndpoint, profile), "Azure DevOps API error ");

            for (JsonNode value : projects.path("value")) {
                String name = value.path("name").asText();
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
        } catch (Exception ignored) {
            if (!context.inferredProject.isBlank()) {
                names.add(context.inferredProject);
            }
        }
        return names;
    }

    public List<RemoteRepository> fetchAzureRepositories(String providerId, String collectionName, String projectName) throws Exception {
        GitProviderProfile profile = findProviderProfile(providerId);
        if (profile == null) {
            return List.of();
        }

        String providerType = safe(profile.getProviderType()).toLowerCase();
        if (providerType.isBlank()) {
            providerType = settingsService.inferProviderType(profile.getProviderUrl());
        }
        if (!"azure".equals(providerType)) {
            return List.of();
        }

        String selectedProject = safe(projectName);
        URI providerUri = URI.create(safe(profile.getProviderUrl()));
        AzureBrowseContext context = resolveAzureBrowseContext(providerUri);
        if (selectedProject.isBlank()) {
            selectedProject = context.inferredProject;
        }
        if (selectedProject.isBlank()) {
            return List.of();
        }

        String selectedCollection = safe(collectionName);
        if (selectedCollection.isBlank()) {
            selectedCollection = context.inferredCollection;
        }

        String reposEndpoint = buildAzureCollectionBaseUrl(context, selectedCollection)
                + "/" + encode(selectedProject)
                + "/_apis/git/repositories?api-version=7.1-preview.1";

        JsonNode root = readJson(withBasicAuth(reposEndpoint, profile), "Azure DevOps API error ");
        List<RemoteRepository> repositories = new ArrayList<>();
        for (JsonNode node : root.path("value")) {
            String repoName = node.path("name").asText();
            String remoteUrl = node.path("remoteUrl").asText();
            if (repoName.isBlank() || remoteUrl.isBlank()) {
                continue;
            }
            repositories.add(new RemoteRepository(
                    repoName,
                    selectedProject + "/" + repoName,
                    remoteUrl,
                    node.path("defaultBranch").asText("refs/heads/main").replace("refs/heads/", ""),
                    !node.path("project").isMissingNode(),
                    safe(profile.getId()),
                    safe(profile.getName()),
                    "azure"
            ));
        }
        return repositories;
    }

    private List<RemoteRepository> fetchGitHubRepositories(GitProviderProfile profile) throws Exception {
        String pat = safe(profile.getPat());
        String username = safe(profile.getUsername());

        if (pat.isEmpty() && username.isEmpty()) {
            return List.of();
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

        JsonNode root = readJson(requestBuilder.build(), "GitHub API error ");
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
                    node.path("private").asBoolean(false),
                    safe(profile.getId()),
                    safe(profile.getName()),
                    "github"
            ));
        }
        return repositories;
    }

    private List<RemoteRepository> fetchGitLabRepositories(GitProviderProfile profile) throws Exception {
        return fetchGitLabProjects(safe(profile.getId()), extractFullPathAfterHost(safe(profile.getProviderUrl())));
    }

    private List<RemoteRepository> fetchAzureRepositories(GitProviderProfile profile) throws Exception {
        String providerUrl = safe(profile.getProviderUrl());
        if (providerUrl.isBlank()) {
            return List.of();
        }

        URI providerUri = URI.create(providerUrl);
        String[] pathSegments = providerUri.getPath() == null ? new String[0] : providerUri.getPath().split("/");
        List<String> nonEmptySegments = new ArrayList<>();
        for (String pathSegment : pathSegments) {
            if (!pathSegment.isBlank()) {
                nonEmptySegments.add(pathSegment);
            }
        }

        AzureEndpointInfo endpointInfo = resolveAzureEndpointInfo(providerUri, nonEmptySegments);
        if (endpointInfo.baseUrl.isBlank()) {
            return List.of();
        }

        List<String> projectNames = new ArrayList<>();
        if (!endpointInfo.projectName.isBlank()) {
            projectNames.add(endpointInfo.projectName);
        } else {
            String projectsEndpoint = endpointInfo.baseUrl + "/_apis/projects?api-version=7.1-preview.4";
            JsonNode projects = readJson(withBasicAuth(projectsEndpoint, profile), "Azure DevOps API error ");
            for (JsonNode value : projects.path("value")) {
                String name = value.path("name").asText();
                if (!name.isBlank()) {
                    projectNames.add(name);
                }
            }
        }

        List<RemoteRepository> repositories = new ArrayList<>();
        for (String projectName : projectNames) {
            String reposEndpoint = endpointInfo.baseUrl + "/" + encode(projectName) + "/_apis/git/repositories?api-version=7.1-preview.1";
            JsonNode root = readJson(withBasicAuth(reposEndpoint, profile), "Azure DevOps API error ");
            for (JsonNode node : root.path("value")) {
                String repoName = node.path("name").asText();
                String remoteUrl = node.path("remoteUrl").asText();
                if (repoName.isBlank() || remoteUrl.isBlank()) {
                    continue;
                }
                repositories.add(new RemoteRepository(
                        repoName,
                        projectName + "/" + repoName,
                        remoteUrl,
                        node.path("defaultBranch").asText("refs/heads/main").replace("refs/heads/", ""),
                        !node.path("project").isMissingNode(),
                        safe(profile.getId()),
                        safe(profile.getName()),
                        "azure"
                ));
            }
        }
        return repositories;
    }

    private AzureEndpointInfo resolveAzureEndpointInfo(URI providerUri, List<String> pathSegments) {
        String scheme = safe(providerUri.getScheme()).isBlank() ? "https" : safe(providerUri.getScheme());
        String host = safe(providerUri.getHost());
        if (host.isBlank()) {
            return new AzureEndpointInfo("", "");
        }

        int gitIndex = pathSegments.indexOf("_git");
        String projectName = "";
        List<String> contextSegments = new ArrayList<>();

        if (gitIndex > 0) {
            projectName = pathSegments.get(gitIndex - 1);
            contextSegments.addAll(pathSegments.subList(0, gitIndex - 1));
        } else if (pathSegments.size() >= 2) {
            contextSegments.add(pathSegments.get(0));
            projectName = pathSegments.get(1);
        } else if (pathSegments.size() == 1) {
            contextSegments.add(pathSegments.get(0));
        }

        String baseHost = scheme + "://" + host;
        if (providerUri.getPort() > 0) {
            baseHost += ":" + providerUri.getPort();
        }

        String basePath;
        if (host.contains("dev.azure.com")) {
            if (contextSegments.isEmpty() && !pathSegments.isEmpty()) {
                contextSegments.add(pathSegments.get(0));
            }
            basePath = joinEncodedSegments(contextSegments);
        } else if (host.contains("visualstudio.com")) {
            basePath = joinEncodedSegments(contextSegments);
        } else {
            // Azure DevOps Server / TFS style URLs typically include collection in the first segment.
            basePath = joinEncodedSegments(contextSegments);
        }

        String baseUrl = basePath.isBlank() ? baseHost : baseHost + "/" + basePath;
        return new AzureEndpointInfo(baseUrl, projectName);
    }

    private List<RemoteRepository> fetchBitbucketRepositories(GitProviderProfile profile) throws Exception {
        String providerUrl = safe(profile.getProviderUrl());
        String workspace = extractFirstPathSegment(providerUrl);
        if (workspace.isBlank()) {
            workspace = safe(profile.getUsername());
        }
        if (workspace.isBlank()) {
            return List.of();
        }

        String endpoint = "https://api.bitbucket.org/2.0/repositories/" + encode(workspace) + "?pagelen=100";
        JsonNode root = readJson(withBasicAuth(endpoint, profile), "Bitbucket API error ");

        List<RemoteRepository> repositories = new ArrayList<>();
        for (JsonNode node : root.path("values")) {
            String repoName = node.path("name").asText();
            String fullName = node.path("full_name").asText(repoName);
            String cloneUrl = "";
            for (JsonNode cloneNode : node.path("links").path("clone")) {
                if ("https".equalsIgnoreCase(cloneNode.path("name").asText())) {
                    cloneUrl = cloneNode.path("href").asText();
                    break;
                }
            }
            if (cloneUrl.isBlank()) {
                continue;
            }
            repositories.add(new RemoteRepository(
                    repoName,
                    fullName,
                    cloneUrl,
                    node.path("mainbranch").path("name").asText("main"),
                    node.path("is_private").asBoolean(false),
                    safe(profile.getId()),
                    safe(profile.getName()),
                    "bitbucket"
            ));
        }
        return repositories;
    }

    private HttpRequest withBasicAuth(String endpoint, GitProviderProfile profile) {
        String username = safe(profile.getUsername());
        String secret = safe(profile.getPat());
        if (secret.isBlank()) {
            secret = safe(profile.getPassword());
        }
        if (username.isBlank() && !secret.isBlank()) {
            username = "pat";
        }
        String encoded = Base64.getEncoder().encodeToString((username + ":" + secret).getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + encoded)
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private ProviderVerificationResult verifyGitHubProvider(GitProviderProfile profile) throws Exception {
        String baseApi = buildGitHubApiBaseUrl(profile);
        String pat = safe(profile.getPat());
        String username = safe(profile.getUsername());
        String endpoint = !pat.isBlank()
                ? baseApi + "/user"
                : baseApi + "/users/" + encode(username.isBlank() ? extractFirstPathSegment(safe(profile.getProviderUrl())) : username);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .GET();
        if (!pat.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + pat);
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        return new ProviderVerificationResult(response.statusCode() < 300, response.statusCode(),
                response.statusCode() < 300 ? "GitHub connection verified." : "GitHub verification failed.", "github");
    }

    private ProviderVerificationResult verifyGitLabProvider(GitProviderProfile profile) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildGitLabApiBaseUrl(profile) + "/user"))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("PRIVATE-TOKEN", resolveSecret(profile))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new ProviderVerificationResult(response.statusCode() < 300, response.statusCode(),
                response.statusCode() < 300 ? "GitLab connection verified." : "GitLab verification failed.", "gitlab");
    }

    private ProviderVerificationResult verifyAzureProvider(GitProviderProfile profile) throws Exception {
        URI providerUri = URI.create(safe(profile.getProviderUrl()));
        AzureBrowseContext context = resolveAzureBrowseContext(providerUri);
        String endpoint;
        if (!context.inferredCollection.isBlank() && !context.inferredProject.isBlank()) {
            endpoint = buildAzureCollectionBaseUrl(context, context.inferredCollection)
                    + "/" + encode(context.inferredProject)
                    + "/_apis/git/repositories?api-version=7.1-preview.1";
        } else if (!context.inferredCollection.isBlank()) {
            endpoint = buildAzureCollectionBaseUrl(context, context.inferredCollection)
                    + "/_apis/projects?api-version=7.1-preview.4";
        } else {
            endpoint = context.baseHost;
            if (!context.pathPrefix.isBlank()) {
                endpoint += "/" + context.pathPrefix;
            }
            endpoint += "/_apis/projectcollections?api-version=7.1-preview.1";
        }
        HttpResponse<String> response = httpClient.send(withBasicAuth(endpoint, profile), HttpResponse.BodyHandlers.ofString());
        return new ProviderVerificationResult(response.statusCode() < 300, response.statusCode(),
                response.statusCode() < 300 ? "Azure DevOps connection verified." : "Azure DevOps verification failed.", "azure");
    }

    private ProviderVerificationResult verifyBitbucketProvider(GitProviderProfile profile) throws Exception {
        String workspace = extractFirstPathSegment(safe(profile.getProviderUrl()));
        if (workspace.isBlank()) {
            workspace = safe(profile.getUsername());
        }
        String endpoint = "https://api.bitbucket.org/2.0/repositories/" + encode(workspace) + "?pagelen=1";
        HttpResponse<String> response = httpClient.send(withBasicAuth(endpoint, profile), HttpResponse.BodyHandlers.ofString());
        return new ProviderVerificationResult(response.statusCode() < 300, response.statusCode(),
                response.statusCode() < 300 ? "Bitbucket connection verified." : "Bitbucket verification failed.", "bitbucket");
    }

    private String buildGitHubApiBaseUrl(GitProviderProfile profile) {
        URI uri = URI.create(safe(profile.getProviderUrl()).isBlank() ? "https://github.com" : safe(profile.getProviderUrl()));
        String host = safe(uri.getHost()).toLowerCase();
        String scheme = safe(uri.getScheme()).isBlank() ? "https" : safe(uri.getScheme());
        if (host.equals("github.com")) {
            return "https://api.github.com";
        }

        String base = scheme + "://" + host;
        if (uri.getPort() > 0) {
            base += ":" + uri.getPort();
        }
        return base + "/api/v3";
    }

    private String buildGitLabApiBaseUrl(GitProviderProfile profile) {
        URI uri = URI.create(safe(profile.getProviderUrl()).isBlank() ? "https://gitlab.com" : safe(profile.getProviderUrl()));
        String scheme = safe(uri.getScheme()).isBlank() ? "https" : safe(uri.getScheme());
        String host = safe(uri.getHost());
        String base = scheme + "://" + host;
        if (uri.getPort() > 0) {
            base += ":" + uri.getPort();
        }
        return base + "/api/v4";
    }

    private String resolveSecret(GitProviderProfile profile) {
        String secret = safe(profile.getPat());
        if (secret.isBlank()) {
            secret = safe(profile.getPassword());
        }
        return secret;
    }

    private String extractFullPathAfterHost(String url) {
        try {
            URI uri = URI.create(url);
            String path = safe(uri.getPath());
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return path;
        } catch (Exception ignored) {
        }
        return "";
    }

    private GitProviderProfile findProviderProfile(String providerId) {
        String normalizedProviderId = safe(providerId);
        if (normalizedProviderId.isBlank()) {
            return null;
        }

        GitProviderSettings settings = settingsService.getSettings();
        for (GitProviderProfile profile : settings.getProviders()) {
            if (normalizedProviderId.equals(safe(profile.getId()))) {
                return profile;
            }
        }
        return null;
    }

    private AzureBrowseContext resolveAzureBrowseContext(URI providerUri) {
        String scheme = safe(providerUri.getScheme()).isBlank() ? "https" : safe(providerUri.getScheme());
        String host = safe(providerUri.getHost());
        if (host.isBlank()) {
            return new AzureBrowseContext("", "", "", "", false);
        }

        List<String> segments = new ArrayList<>();
        String[] pathSegments = safe(providerUri.getPath()).split("/");
        for (String segment : pathSegments) {
            if (!segment.isBlank()) {
                segments.add(segment);
            }
        }

        boolean isDevAzureHost = host.contains("dev.azure.com");
        boolean isVisualStudioHost = host.contains("visualstudio.com");
        String inferredCollection = "";
        String inferredProject = "";
        List<String> prefixSegments = new ArrayList<>();

        if (isDevAzureHost) {
            if (!segments.isEmpty()) {
                inferredCollection = segments.get(0);
            }
            if (segments.size() >= 2) {
                inferredProject = segments.get(1);
            }
        } else if (isVisualStudioHost) {
            if (!segments.isEmpty()) {
                inferredProject = segments.get(0);
            }
        } else {
            int gitIndex = segments.indexOf("_git");
            if (gitIndex >= 2) {
                inferredCollection = segments.get(gitIndex - 2);
                inferredProject = segments.get(gitIndex - 1);
                prefixSegments.addAll(segments.subList(0, gitIndex - 2));
            } else if (!segments.isEmpty()) {
                if ("tfs".equalsIgnoreCase(segments.get(0))) {
                    prefixSegments.add(segments.get(0));
                    if (segments.size() >= 2) {
                        inferredCollection = segments.get(1);
                    }
                    if (segments.size() >= 3) {
                        inferredProject = segments.get(2);
                    }
                } else {
                    inferredCollection = segments.get(0);
                    if (segments.size() >= 2) {
                        inferredProject = segments.get(1);
                    }
                }
            }
        }

        String baseHost = scheme + "://" + host;
        if (providerUri.getPort() > 0) {
            baseHost += ":" + providerUri.getPort();
        }

        return new AzureBrowseContext(baseHost, joinEncodedSegments(prefixSegments), inferredCollection, inferredProject, isDevAzureHost);
    }

    private String buildAzureCollectionBaseUrl(AzureBrowseContext context, String collectionName) {
        String baseUrl = context.baseHost;
        if (!context.pathPrefix.isBlank()) {
            baseUrl += "/" + context.pathPrefix;
        }

        String collection = safe(collectionName);
        if (!collection.isBlank()) {
            baseUrl += "/" + encode(collection);
        }
        return baseUrl;
    }

    private JsonNode readJson(HttpRequest request, String errorPrefix) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            String body = response.body() == null ? "" : response.body();
            throw new IOException(errorPrefix + response.statusCode() + ": " + body);
        }
        return objectMapper.readTree(response.body());
    }

    private String extractFirstPathSegment(String url) {
        try {
            URI uri = URI.create(url);
            String path = safe(uri.getPath());
            for (String segment : path.split("/")) {
                if (!segment.isBlank()) {
                    return segment;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String joinEncodedSegments(List<String> segments) {
        if (segments == null || segments.isEmpty()) {
            return "";
        }

        List<String> encoded = new ArrayList<>();
        for (String segment : segments) {
            String trimmed = safe(segment);
            if (!trimmed.isBlank()) {
                encoded.add(encode(trimmed));
            }
        }
        return String.join("/", encoded);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class AzureEndpointInfo {
        private final String baseUrl;
        private final String projectName;

        private AzureEndpointInfo(String baseUrl, String projectName) {
            this.baseUrl = baseUrl;
            this.projectName = projectName;
        }
    }

    private static final class AzureBrowseContext {
        private final String baseHost;
        private final String pathPrefix;
        private final String inferredCollection;
        private final String inferredProject;
        private final boolean isDevAzureHost;

        private AzureBrowseContext(String baseHost, String pathPrefix, String inferredCollection, String inferredProject, boolean isDevAzureHost) {
            this.baseHost = baseHost;
            this.pathPrefix = pathPrefix;
            this.inferredCollection = inferredCollection;
            this.inferredProject = inferredProject;
            this.isDevAzureHost = isDevAzureHost;
        }
    }
}
