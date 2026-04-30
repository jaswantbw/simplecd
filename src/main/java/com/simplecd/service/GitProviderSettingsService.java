// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
package com.simplecd.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplecd.model.GitProviderProfile;
import com.simplecd.model.GitProviderSettings;
import com.simplecd.model.ResolvedGitProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@Service
public class GitProviderSettingsService {

    private static final String PROFILES_JSON_KEY = "profilesJson";

    private final GitProviderSettings settings = new GitProviderSettings();
    private final Path settingsFilePath;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GitProviderSettingsService(
            @Value("${simplecd.base-dir:C:/SimpleCD}") String baseDir,
            @Value("${simplecd.git.provider-url:}") String defaultProviderUrl,
            @Value("${simplecd.git.username:}") String defaultUsername,
            @Value("${simplecd.git.password:}") String defaultPassword,
            @Value("${simplecd.git.pat:}") String defaultPat,
            @Value("${simplecd.git.ssh-key:}") String defaultSshKey) {
        this.settingsFilePath = Paths.get(baseDir, "config", "git-settings.properties");

        List<GitProviderProfile> defaults = new ArrayList<>();
        if (hasAnyValue(defaultProviderUrl, defaultUsername, defaultPassword, defaultPat, defaultSshKey)) {
            GitProviderProfile profile = new GitProviderProfile();
            profile.setId(UUID.randomUUID().toString());
            profile.setName(defaultProfileName(defaultProviderUrl));
            profile.setProviderType(inferProviderType(defaultProviderUrl));
            profile.setProviderUrl(trimToEmpty(defaultProviderUrl));
            profile.setUsername(trimToEmpty(defaultUsername));
            profile.setPassword(trimToEmpty(defaultPassword));
            profile.setPat(trimToEmpty(defaultPat));
            profile.setSshKey(trimToEmpty(defaultSshKey));
            defaults.add(profile);
        }
        settings.setProviders(defaults);

        loadSettingsFromFile();
    }

    public synchronized GitProviderSettings getSettings() {
        return copy(settings);
    }

    public synchronized GitProviderSettings saveSettings(GitProviderSettings incoming) {
        settings.setProviders(sanitizeProfiles(incoming == null ? List.of() : incoming.getProviders()));
        persistSettingsToFile();
        return copy(settings);
    }

    public synchronized List<GitProviderProfile> getProviderProfiles() {
        return copy(settings).getProviders();
    }

    public synchronized ResolvedGitProfile resolveProfileForRepoUrl(String repoUrl) {
        String normalizedUrl = trimToEmpty(repoUrl);
        List<GitProviderProfile> profiles = sanitizeProfiles(settings.getProviders());
        if (profiles.isEmpty()) {
            return new ResolvedGitProfile(inferProviderType(normalizedUrl), "", "", "", "", "");
        }

        GitProviderProfile directMatch = profiles.stream()
                .filter(profile -> !trimToEmpty(profile.getProviderUrl()).isBlank())
                .sorted(Comparator.comparingInt((GitProviderProfile profile) -> trimToEmpty(profile.getProviderUrl()).length()).reversed())
                .filter(profile -> urlMatches(normalizedUrl, profile.getProviderUrl()))
                .findFirst()
                .orElse(null);

        if (directMatch != null) {
            return toResolvedProfile(directMatch);
        }

        // Host-level fallback: match by scheme+host so any repo on the same server resolves credentials
        String repoHost = extractSchemeAndHost(normalizedUrl);
        if (!repoHost.isBlank()) {
            GitProviderProfile hostMatch = profiles.stream()
                    .filter(profile -> !trimToEmpty(profile.getProviderUrl()).isBlank())
                    .filter(profile -> repoHost.equals(extractSchemeAndHost(trimToEmpty(profile.getProviderUrl()))))
                    .findFirst()
                    .orElse(null);
            if (hostMatch != null) {
                return toResolvedProfile(hostMatch);
            }
        }

        String inferredType = inferProviderType(normalizedUrl);
        GitProviderProfile typeMatch = profiles.stream()
                .filter(profile -> inferredType.equals(trimToEmpty(profile.getProviderType())))
                .findFirst()
                .orElse(null);

        return typeMatch == null
                ? new ResolvedGitProfile(inferredType, "", "", "", "", "")
                : toResolvedProfile(typeMatch);
    }

    public String inferProviderType(String repoUrl) {
        String normalized = trimToEmpty(repoUrl).toLowerCase();
        if (normalized.contains("github.com")) {
            return "github";
        }
        if (normalized.contains("gitlab.com")) {
            return "gitlab";
        }
        if (normalized.contains("dev.azure.com") || normalized.contains("visualstudio.com")) {
            return "azure";
        }
        if (normalized.contains("bitbucket.org")) {
            return "bitbucket";
        }
        // On-prem Azure DevOps Server URLs always contain /_git/ in the path
        if (normalized.contains("/_git/") || normalized.contains("/_git")) {
            return "azure";
        }
        return "other";
    }

    private void loadSettingsFromFile() {
        if (!Files.exists(settingsFilePath)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(settingsFilePath)) {
            properties.load(input);
            String profilesJson = trimToEmpty(properties.getProperty(PROFILES_JSON_KEY));
            if (!profilesJson.isBlank()) {
                List<GitProviderProfile> loadedProfiles = objectMapper.readValue(profilesJson, new TypeReference<List<GitProviderProfile>>() { });
                settings.setProviders(sanitizeProfiles(loadedProfiles));
                return;
            }

            GitProviderProfile legacy = new GitProviderProfile();
            legacy.setId(UUID.randomUUID().toString());
            legacy.setProviderUrl(trimToEmpty(properties.getProperty("providerUrl")));
            legacy.setUsername(trimToEmpty(properties.getProperty("username")));
            legacy.setPassword(trimToEmpty(properties.getProperty("password")));
            legacy.setPat(trimToEmpty(properties.getProperty("pat")));
            legacy.setSshKey(trimToEmpty(properties.getProperty("sshKey")));
            legacy.setProviderType(inferProviderType(legacy.getProviderUrl()));
            legacy.setName(defaultProfileName(legacy.getProviderUrl()));
            if (hasAnyValue(legacy.getProviderUrl(), legacy.getUsername(), legacy.getPassword(), legacy.getPat(), legacy.getSshKey())) {
                settings.setProviders(sanitizeProfiles(List.of(legacy)));
            }
        } catch (IOException e) {
            System.err.println("Unable to read git settings file: " + e.getMessage());
        }
    }

    private void persistSettingsToFile() {
        Properties properties = new Properties();
        List<GitProviderProfile> profiles = sanitizeProfiles(settings.getProviders());
        try {
            properties.setProperty(PROFILES_JSON_KEY, objectMapper.writeValueAsString(profiles));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize git provider settings.", e);
        }

        GitProviderProfile firstProfile = profiles.isEmpty() ? null : profiles.get(0);
        properties.setProperty("providerUrl", firstProfile == null ? "" : trimToEmpty(firstProfile.getProviderUrl()));
        properties.setProperty("username", firstProfile == null ? "" : trimToEmpty(firstProfile.getUsername()));
        properties.setProperty("password", firstProfile == null ? "" : trimToEmpty(firstProfile.getPassword()));
        properties.setProperty("pat", firstProfile == null ? "" : trimToEmpty(firstProfile.getPat()));
        properties.setProperty("sshKey", firstProfile == null ? "" : trimToEmpty(firstProfile.getSshKey()));

        try {
            Path parent = settingsFilePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(settingsFilePath)) {
                properties.store(output, "SimpleCD Git Provider Settings");
            }
        } catch (IOException e) {
            System.err.println("Unable to persist git settings file: " + e.getMessage());
        }
    }

    private GitProviderSettings copy(GitProviderSettings source) {
        GitProviderSettings clone = new GitProviderSettings();
        clone.setProviders(sanitizeProfiles(source.getProviders()));
        return clone;
    }

    private List<GitProviderProfile> sanitizeProfiles(List<GitProviderProfile> incomingProfiles) {
        List<GitProviderProfile> sanitized = new ArrayList<>();
        if (incomingProfiles == null) {
            return sanitized;
        }

        for (GitProviderProfile profile : incomingProfiles) {
            if (profile == null) {
                continue;
            }

            GitProviderProfile clone = new GitProviderProfile();
            clone.setId(trimToEmpty(profile.getId()).isBlank() ? UUID.randomUUID().toString() : trimToEmpty(profile.getId()));
            clone.setProviderUrl(trimToEmpty(profile.getProviderUrl()));
            clone.setProviderType(trimToEmpty(profile.getProviderType()).isBlank()
                    ? inferProviderType(clone.getProviderUrl())
                    : trimToEmpty(profile.getProviderType()).toLowerCase());
            clone.setName(trimToEmpty(profile.getName()).isBlank() ? defaultProfileName(clone.getProviderUrl(), clone.getProviderType()) : trimToEmpty(profile.getName()));
            clone.setUsername(trimToEmpty(profile.getUsername()));
            clone.setPassword(trimToEmpty(profile.getPassword()));
            clone.setPat(trimToEmpty(profile.getPat()));
            clone.setSshKey(trimToEmpty(profile.getSshKey()));

            if (!hasAnyValue(clone.getProviderUrl(), clone.getUsername(), clone.getPassword(), clone.getPat(), clone.getSshKey())) {
                continue;
            }
            sanitized.add(clone);
        }
        return sanitized;
    }

    private ResolvedGitProfile toResolvedProfile(GitProviderProfile profile) {
        return new ResolvedGitProfile(
                trimToEmpty(profile.getProviderType()),
                trimToEmpty(profile.getName()),
                trimToEmpty(profile.getProviderUrl()),
                trimToEmpty(profile.getUsername()),
                trimToEmpty(profile.getPassword()),
                trimToEmpty(profile.getPat())
        );
    }

    private String extractSchemeAndHost(String url) {
        String trimmed = trimToEmpty(url).toLowerCase();
        if (trimmed.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = trimToEmpty(uri.getScheme());
            String host = trimToEmpty(uri.getHost());
            if (scheme.isBlank() || host.isBlank()) {
                return "";
            }
            int port = uri.getPort();
            return scheme + "://" + host + (port > 0 ? ":" + port : "");
        } catch (Exception e) {
            return "";
        }
    }

    private boolean urlMatches(String repoUrl, String providerUrl) {
        String normalizedRepoUrl = normalizeUrl(repoUrl);
        String normalizedProviderUrl = normalizeUrl(providerUrl);
        if (normalizedRepoUrl.isBlank() || normalizedProviderUrl.isBlank()) {
            return false;
        }
        return normalizedRepoUrl.startsWith(normalizedProviderUrl);
    }

    private String normalizeUrl(String value) {
        String trimmed = trimToEmpty(value);
        if (trimmed.isBlank()) {
            return "";
        }

        String normalized = trimmed.toLowerCase();
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String defaultProfileName(String providerUrl) {
        return defaultProfileName(providerUrl, inferProviderType(providerUrl));
    }

    private String defaultProfileName(String providerUrl, String providerType) {
        String url = trimToEmpty(providerUrl);
        if (!url.isBlank()) {
            try {
                URI uri = URI.create(url);
                String host = trimToEmpty(uri.getHost());
                if (!host.isBlank()) {
                    String path = trimToEmpty(uri.getPath());
                    if (!path.isBlank() && !"/".equals(path)) {
                        return host + path.replace('/', ' ').trim();
                    }
                    return host;
                }
            } catch (Exception ignored) {
            }
        }

        return switch (trimToEmpty(providerType)) {
            case "github" -> "GitHub";
            case "gitlab" -> "GitLab";
            case "azure" -> "Azure DevOps";
            case "bitbucket" -> "Bitbucket";
            default -> "Other";
        };
    }

    private boolean hasAnyValue(String... values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (!trimToEmpty(value).isBlank()) {
                return true;
            }
        }
        return false;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
