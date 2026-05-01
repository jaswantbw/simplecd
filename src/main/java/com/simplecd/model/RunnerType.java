// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
package com.simplecd.model;

public enum RunnerType {
    LOCAL,      // direct ProcessBuilder on the same JVM host
    SSH,        // remote server via SSH
    DOCKER,     // docker exec into a running container
    KUBERNETES  // kubectl exec into a running pod
}
