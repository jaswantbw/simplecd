// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
package com.simplecd.model;

public enum RunnerShell {
    PWSH,   // PowerShell Core (pwsh -Command "...")
    CMD,    // Windows CMD  (cmd /c "...")
    BASH    // Bash          (bash -c "...")
}
