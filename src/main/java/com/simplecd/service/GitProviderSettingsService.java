package com.simplecd.service;

import com.simplecd.model.GitProviderSettings;
import org.springframework.stereotype.Service;

@Service
public class GitProviderSettingsService {

    private final GitProviderSettings settings = new GitProviderSettings();

    public synchronized GitProviderSettings getSettings() {
        return copy(settings);
    }

    public synchronized GitProviderSettings saveSettings(GitProviderSettings incoming) {
        settings.setProviderUrl(trimToEmpty(incoming.getProviderUrl()));
        settings.setUsername(trimToEmpty(incoming.getUsername()));
        settings.setPassword(trimToEmpty(incoming.getPassword()));
        settings.setPat(trimToEmpty(incoming.getPat()));
        settings.setSshKey(trimToEmpty(incoming.getSshKey()));
        return copy(settings);
    }

    private GitProviderSettings copy(GitProviderSettings source) {
        GitProviderSettings clone = new GitProviderSettings();
        clone.setProviderUrl(source.getProviderUrl());
        clone.setUsername(source.getUsername());
        clone.setPassword(source.getPassword());
        clone.setPat(source.getPat());
        clone.setSshKey(source.getSshKey());
        return clone;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
