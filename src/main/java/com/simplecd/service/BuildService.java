package com.simplecd.service;

import com.simplecd.model.BuildJob;
import com.simplecd.model.BuildStatus;
import com.simplecd.model.Repository;
import com.simplecd.model.ResolvedGitProfile;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BuildService {

    private final Map<String, BuildJob> jobs = new ConcurrentHashMap<>();
    private final BuildExecutorService buildExecutorService;
    private final RepositoryService repositoryService;
    private final GitProviderSettingsService gitProviderSettingsService;
    private static final String REPOS_FOLDER = "c:\\workspaces";
    private static final long GIT_COMMAND_TIMEOUT_SECONDS = 90;

    public BuildService(BuildExecutorService buildExecutorService,
                        RepositoryService repositoryService,
                        GitProviderSettingsService gitProviderSettingsService) {
        this.buildExecutorService = buildExecutorService;
        this.repositoryService = repositoryService;
        this.gitProviderSettingsService = gitProviderSettingsService;
        initializeReposFolder();
    }

    private void initializeReposFolder() {
        try {
            Path reposPath = Paths.get(REPOS_FOLDER);
            if (!Files.exists(reposPath)) {
                Files.createDirectories(reposPath);
            }
        } catch (IOException e) {
            System.err.println("Error creating repos folder: " + e.getMessage());
        }
    }

    public void cloneRepository(String repoUrl, String pat, String defaultBranch) throws Exception {
        ResolvedGitProfile resolvedProfile = gitProviderSettingsService.resolveProfileForRepoUrl(repoUrl);
        String resolvedPat = resolvePat(repoUrl, pat);

        // Extract repo name from URL
        String repoName = repoUrl.substring(repoUrl.lastIndexOf("/") + 1).replace(".git", "");
        Path repoPath = Paths.get(REPOS_FOLDER, repoName);

        if (repositoryService.findByUrl(repoUrl) != null || Files.exists(repoPath)) {
            throw new IllegalArgumentException("Repository already exists: " + repoName);
        }

        String output = runCommand(withRemoteAuth(Arrays.asList(
                "git", "clone", "--depth", "1", repoUrl, repoPath.toString()
        ), repoUrl, resolvedProfile, resolvedPat));

        // Save repository to service after successful clone
        String branchToSave = (defaultBranch == null || defaultBranch.isBlank()) ? "main" : defaultBranch;
        repositoryService.addRepository(repoName, repoUrl, repoPath.toString(), branchToSave, gitProviderSettingsService.inferProviderType(repoUrl));
    }

    public String cloneRepositoryById(String repositoryId, String branch) throws Exception {
        Repository repo = repositoryService.getRepository(repositoryId);
        if (repo == null) {
            throw new IllegalArgumentException("Repository not found.");
        }

        Path repoPath = Paths.get(repo.getLocalPath());
        if (Files.exists(repoPath)) {
            return "Already cloned: " + repo.getName();
        }

        ResolvedGitProfile resolvedProfile = gitProviderSettingsService.resolveProfileForRepoUrl(repo.getUrl());
        String resolvedPat = resolvePat(repo.getUrl(), "");
        List<String> command = new ArrayList<>();
        command.addAll(Arrays.asList("git", "clone", "--depth", "1"));
        if (branch != null && !branch.isBlank()) {
            command.add("--branch");
            command.add(branch.trim());
        }
        command.add(repo.getUrl());
        command.add(repoPath.toString());

        String output = runCommand(withRemoteAuth(command, repo.getUrl(), resolvedProfile, resolvedPat));
        repo.setDefaultBranch((branch == null || branch.isBlank()) ? repo.getDefaultBranch() : branch.trim());
        return "Clone command executed. " + shortenOutput(output);
    }

    public String pullRepository(String repositoryId, String branch) throws Exception {
        Repository repo = repositoryService.getRepository(repositoryId);
        if (repo == null) {
            throw new IllegalArgumentException("Repository not found.");
        }

        Path repoPath = Paths.get(repo.getLocalPath());
        if (!Files.exists(repoPath)) {
            throw new IllegalArgumentException("Repository folder is missing. Use Clone first.");
        }

        String branchName = (branch == null || branch.isBlank()) ? repo.getDefaultBranch() : branch.trim();
        if (branchName == null || branchName.isBlank()) {
            branchName = "main";
        }

        runCommand(Arrays.asList("git", "-C", repoPath.toString(), "checkout", branchName));
        String output = runCommand(withRemoteAuth(
            Arrays.asList("git", "-C", repoPath.toString(), "pull", "origin", branchName),
            repo.getUrl(),
            gitProviderSettingsService.resolveProfileForRepoUrl(repo.getUrl()),
            resolvePat(repo.getUrl(), "")
        ));
        repo.setDefaultBranch(branchName);
        return "Pull command executed: git pull origin " + branchName + ". " + shortenOutput(output);
    }

    public String fetchRepository(String repositoryId) throws Exception {
        Repository repo = repositoryService.getRepository(repositoryId);
        if (repo == null) {
            throw new IllegalArgumentException("Repository not found.");
        }

        Path repoPath = Paths.get(repo.getLocalPath());
        if (!Files.exists(repoPath)) {
            throw new IllegalArgumentException("Repository folder is missing. Use Clone first.");
        }

        String output = runCommand(withRemoteAuth(
            Arrays.asList("git", "-C", repoPath.toString(), "fetch", "--all", "--tags", "--prune"),
            repo.getUrl(),
            gitProviderSettingsService.resolveProfileForRepoUrl(repo.getUrl()),
            resolvePat(repo.getUrl(), "")
        ));
        return "Fetch command executed: git fetch --all --tags --prune. " + shortenOutput(output);
    }

    public String checkoutRepository(String repositoryId, String branch) throws Exception {
        Repository repo = repositoryService.getRepository(repositoryId);
        if (repo == null) {
            throw new IllegalArgumentException("Repository not found.");
        }

        Path repoPath = Paths.get(repo.getLocalPath());
        if (!Files.exists(repoPath)) {
            throw new IllegalArgumentException("Repository folder is missing. Use Clone first.");
        }

        String branchName = (branch == null || branch.isBlank()) ? repo.getDefaultBranch() : branch.trim();
        if (branchName == null || branchName.isBlank()) {
            branchName = "main";
        }

        String remoteRef = "refs/remotes/origin/" + branchName;
        String refSpec = "+refs/heads/" + branchName + ":" + remoteRef;
        runCommand(withRemoteAuth(
            Arrays.asList("git", "-C", repoPath.toString(), "fetch", "origin", refSpec),
            repo.getUrl(),
            gitProviderSettingsService.resolveProfileForRepoUrl(repo.getUrl()),
            resolvePat(repo.getUrl(), "")
        ));
        try {
            runCommand(Arrays.asList("git", "-C", repoPath.toString(), "checkout", branchName));
        } catch (Exception checkoutError) {
            // If local branch does not exist yet, create it from fetched remote-tracking ref.
            runCommand(Arrays.asList("git", "-C", repoPath.toString(), "checkout", "-B", branchName, remoteRef));
        }

        repo.setDefaultBranch(branchName);
        return "Checkout command executed: git checkout " + branchName + ". Local source switched to selected branch.";
    }

    public List<String> getRepositoryBranches(String repositoryId) throws Exception {
        Repository repo = repositoryService.getRepository(repositoryId);
        if (repo == null) {
            throw new IllegalArgumentException("Repository not found.");
        }

        String resolvedPat = resolvePat(repo.getUrl(), "");
        ResolvedGitProfile resolvedProfile = gitProviderSettingsService.resolveProfileForRepoUrl(repo.getUrl());
        String output = runCommand(withRemoteAuth(
            Arrays.asList("git", "ls-remote", "--heads", repo.getUrl()),
            repo.getUrl(),
            resolvedProfile,
            resolvedPat
        ));
        LinkedHashSet<String> branches = new LinkedHashSet<>();
        for (String line : output.split("\\R")) {
            int refIndex = line.indexOf("refs/heads/");
            if (refIndex < 0) {
                continue;
            }
            String branch = line.substring(refIndex + "refs/heads/".length()).trim();
            if (!branch.isBlank()) {
                branches.add(branch);
            }
        }

        String defaultBranch = (repo.getDefaultBranch() == null || repo.getDefaultBranch().isBlank())
                ? "main"
                : repo.getDefaultBranch().trim();

        if (branches.isEmpty()) {
            return new ArrayList<>(Collections.singletonList(defaultBranch));
        }

        List<String> ordered = new ArrayList<>();
        if (branches.remove(defaultBranch)) {
            ordered.add(defaultBranch);
        }
        ordered.addAll(branches);
        return ordered;
    }

    public void cloneRepository(String repoUrl, String pat) throws Exception {
        cloneRepository(repoUrl, pat, "main");
    }

    public void cloneRepository(String repoUrl) throws Exception {
        cloneRepository(repoUrl, "", "main");
    }

    private String resolvePat(String repoUrl, String requestPat) {
        if (requestPat != null && !requestPat.isBlank()) {
            return requestPat.trim();
        }

        ResolvedGitProfile profile = gitProviderSettingsService.resolveProfileForRepoUrl(repoUrl);
        if (!profile.getPat().isBlank()) {
            return profile.getPat().trim();
        }
        if (!profile.getPassword().isBlank()) {
            return profile.getPassword().trim();
        }
        return "";
    }

    private String withCredentials(String repoUrl, ResolvedGitProfile profile, String secret) {
        if (repoUrl == null || repoUrl.isBlank() || !repoUrl.startsWith("https://") || secret == null || secret.isBlank()) {
            return repoUrl;
        }

        String username = profile == null ? "" : profile.getUsername();
        String providerType = profile == null ? "" : profile.getProviderType();
        if (username == null || username.isBlank()) {
            if ("github".equals(providerType) || "gitlab".equals(providerType)) {
                username = "oauth2";
            } else if ("azure".equals(providerType)) {
                username = "pat";
            } else {
                username = "git";
            }
        }

        return repoUrl.replace(
                "https://",
                "https://" + URLEncoder.encode(username, StandardCharsets.UTF_8) + ":" + URLEncoder.encode(secret, StandardCharsets.UTF_8) + "@"
        );
    }

    private List<String> withRemoteAuth(List<String> command, String repoUrl, ResolvedGitProfile profile, String secret) {
        if (repoUrl == null || repoUrl.isBlank() || !repoUrl.startsWith("https://") || secret == null || secret.isBlank()) {
            return command;
        }

        String username = profile == null ? "" : profile.getUsername();
        String providerType = profile == null ? "" : profile.getProviderType();
        if (username == null || username.isBlank()) {
            if ("github".equals(providerType) || "gitlab".equals(providerType)) {
                username = "oauth2";
            } else if ("azure".equals(providerType)) {
                username = "pat";
            } else {
                username = "git";
            }
        }

        String headerValue = Base64.getEncoder().encodeToString((username + ":" + secret).getBytes(StandardCharsets.UTF_8));
        List<String> commandWithAuth = new ArrayList<>();
        commandWithAuth.add(command.get(0));
        commandWithAuth.add("-c");
        commandWithAuth.add("http.extraHeader=Authorization: Basic " + headerValue);
        commandWithAuth.addAll(command.subList(1, command.size()));
        return commandWithAuth;
    }

    public BuildJob startBuild(String repoUrl, String branch) {

        String jobId = UUID.randomUUID().toString();

        BuildJob job = new BuildJob();
        job.setJobId(jobId);
        job.setRepoUrl(repoUrl);
        job.setBranch(branch);
        job.setStatus(BuildStatus.QUEUED);
        job.setStartTime(LocalDateTime.now());

        jobs.put(jobId, job);

        buildExecutorService.executeBuild(job);

        return job;
    }

    public Collection<BuildJob> getAllJobs() {
        return jobs.values();
    }

    public Collection<?> getAllRepositories() {
        return repositoryService.getAllRepositories();
    }

    public BuildJob getJob(String jobId) {
        return jobs.get(jobId);
    }

    public String readLogs(String jobId) {
        return buildExecutorService.readLogFile(jobId);
    }

    private String runCommand(List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        pb.environment().put("GCM_INTERACTIVE", "Never");
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(GIT_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Git command timed out after " + GIT_COMMAND_TIMEOUT_SECONDS + "s: " + redactCommand(command));
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new Exception(output.toString().isBlank() ? "Git command failed." : output.toString());
        }
        return output.toString();
    }

    private String redactCommand(List<String> command) {
        List<String> redacted = new ArrayList<>();
        for (String part : command) {
            if (part != null && part.contains("http.extraHeader=Authorization: Basic ")) {
                redacted.add("http.extraHeader=Authorization: Basic ***");
            } else {
                redacted.add(part);
            }
        }
        return String.join(" ", redacted);
    }

    private String shortenOutput(String output) {
        String trimmed = output == null ? "" : output.trim();
        if (trimmed.isBlank()) {
            return "Done.";
        }
        return trimmed.length() > 220 ? trimmed.substring(0, 220) + "..." : trimmed;
    }
}