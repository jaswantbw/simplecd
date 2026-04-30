// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
package com.simplecd.service;

import com.simplecd.model.Repository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RepositoryService {

    private final Map<String, Repository> repositories = new ConcurrentHashMap<>();

    public Repository addRepository(String name, String url, String localPath, String defaultBranch, String providerType) {
        String id = UUID.randomUUID().toString();
        Repository repo = new Repository(id, name, url, localPath, defaultBranch, providerType);
        repositories.put(id, repo);
        return repo;
    }

    public Collection<Repository> getAllRepositories() {
        return repositories.values();
    }

    public Repository getRepository(String id) {
        return repositories.get(id);
    }

    public void deleteRepository(String id) {
        repositories.remove(id);
    }

    public Repository findByUrl(String url) {
        return repositories.values().stream()
                .filter(r -> r.getUrl().equals(url))
                .findFirst()
                .orElse(null);
    }
}
