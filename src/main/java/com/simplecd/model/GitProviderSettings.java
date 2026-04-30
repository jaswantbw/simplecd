// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
package com.simplecd.model;

import java.util.ArrayList;
import java.util.List;

public class GitProviderSettings {
    private List<GitProviderProfile> providers = new ArrayList<>();

    public GitProviderSettings() {
    }

    public List<GitProviderProfile> getProviders() {
        return providers;
    }

    public void setProviders(List<GitProviderProfile> providers) {
        this.providers = providers == null ? new ArrayList<>() : providers;
    }
}
