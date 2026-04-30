# Upgrade Plan: simplecd (20260430104551)

- **Generated**: 2026-04-30 10:45:51
- **HEAD Branch**: main
- **HEAD Commit ID**: a701f9b

## Available Tools

**JDKs**
- JDK 21.0.10: C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\bin (current project JDK, used by step 2)
- JDK 25: **<TO_BE_INSTALLED>** (required by steps 3–5)

**Build Tools**
- Maven 3.9.15: C:\Users\jaswant.sharma\.maven\maven-3.9.15\bin (installed; no wrapper present)
  - Note: Maven 4.0+ is the recommended minimum for Java 25. Maven 3.9.15 is used here as Maven 4.0 is not available via the install tool. Compilation will be ensured via maven-compiler-plugin 3.13+.

## Guidelines

> Note: You can add any specific guidelines or constraints for the upgrade process here if needed, bullet points are preferred.

## Options

- Working branch: appmod/java-upgrade-20260430104551
- Run tests before and after the upgrade: true

## Upgrade Goals

- Java: 21 → **25** (latest LTS)

## Technology Stack

| Technology/Dependency     | Current | Min Compatible | Why Incompatible                                             |
| ------------------------- | ------- | -------------- | ------------------------------------------------------------ |
| Java                      | 21      | 25             | User requested                                               |
| Spring Boot               | 3.3.5   | 3.3.5          | Spring Boot 3.3.x supports Java 25; no upgrade required     |
| Maven                     | 3.9.15  | 4.0.0          | Maven 4.0+ recommended for Java 25; using 3.9.15 with compiler plugin upgrade |
| maven-compiler-plugin     | managed by Spring Boot 3.3.5 (~3.11) | 3.13.0 | Older versions cannot reliably compile Java 25 bytecode      |
| spring-boot-starter-web   | 3.3.5   | -              | Compatible with Java 25                                      |
| spring-boot-starter-thymeleaf | 3.3.5 | -            | Compatible with Java 25                                      |
| spring-boot-starter-test  | 3.3.5   | -              | Compatible with Java 25                                      |

## Derived Upgrades

- **maven-compiler-plugin → 3.13.0+**: Java 25 requires `--release 25`; older managed versions (~3.11) may not recognise release 25. Explicit override in `pom.xml` required.
- **`java.version` property → 25**: Must be updated in `pom.xml` to compile and target Java 25 bytecode.

## Upgrade Steps

- Step 1: Setup Environment — Install JDK 25
  - **Rationale**: JDK 25 is required for all subsequent compilation and test steps. Only JDK 21 is currently available.
  - **Changes to Make**:
    - Install JDK 25 via `#appmod-install-jdk`
    - Verify installation with `#appmod-list-jdks`
  - **Verification**: JDK 25 listed, path confirmed
  - **JDK**: N/A (installer step)

- Step 2: Setup Baseline — Compile and test with current JDK 21
  - **Rationale**: Establish baseline compilation and test pass rate before any changes, forming the acceptance criteria for the upgraded project.
  - **Changes to Make**:
    - Run `mvn clean compile test-compile -q` with JDK 21
    - Run `mvn clean test -q` with JDK 21
    - Document results
  - **Verification**: `mvn clean test -q` succeeds; record pass/fail counts
  - **JDK**: 21 (C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot)

- Step 3: Upgrade Java version and compiler plugin in pom.xml
  - **Rationale**: Update `java.version` to 25 and pin `maven-compiler-plugin` to 3.13.0 to ensure Java 25 bytecode compilation. This is the core upgrade step.
  - **Changes to Make**:
    - Change `<java.version>21</java.version>` → `<java.version>25</java.version>` in `pom.xml`
    - Add explicit `maven-compiler-plugin` version 3.13.0 in `<build><plugins>` section of `pom.xml`
  - **Verification**: `mvn clean test-compile -q` with JDK 25 succeeds (both main and test code compile)
  - **JDK**: 25

- Step 4: Final Validation — Full build and test with Java 25
  - **Rationale**: Verify all upgrade goals are met, all tests pass at the same rate as baseline (100%), and the project is production-ready on Java 25.
  - **Changes to Make**:
    - Run `mvn clean test` with JDK 25
    - Fix any test failures (iterative loop until 100% pass)
    - Resolve any remaining TODOs/workarounds
  - **Verification**: `mvn clean test` succeeds with 100% test pass rate (≥ baseline), JDK 25 in effect
  - **JDK**: 25

## Key Challenges

- **maven-compiler-plugin version recognition of Java 25**
  - **Challenge**: The version managed by Spring Boot 3.3.5 parent POM (~3.11.x) may not recognise `--release 25`, causing a compilation error.
  - **Strategy**: Explicitly override `maven-compiler-plugin` to 3.13.0 in `pom.xml` `<build><plugins>`.

- **No JDK source-code incompatibilities found**
  - Source scan of `src/**/*.java` found no usage of encapsulated APIs, removed packages, or deprecated-for-removal APIs. No source-level changes required.
