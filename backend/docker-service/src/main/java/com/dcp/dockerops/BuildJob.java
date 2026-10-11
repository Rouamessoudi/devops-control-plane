package com.dcp.dockerops;

import com.dcp.common.error.ApiException;
import com.dcp.common.policy.ResourcePolicies;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class BuildJob {

    private static final Logger log = LoggerFactory.getLogger(BuildJob.class);

    private final BuildStore store;
    private final DockerGateway docker;
    private final GithubProperties github;
    private final DockerProperties dockerProperties;

    public BuildJob(BuildStore store, DockerGateway docker, GithubProperties github, DockerProperties dockerProperties) {
        this.store = store;
        this.docker = docker;
        this.github = github;
        this.dockerProperties = dockerProperties;
    }

    public void run(UUID id, String dockerfilePath, String contextPath, String pushImage) {
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("dcp-src-");
            store.markRunning(id);
            int order = 1;
            if (!checkout(id, order++, workspace)) {
                store.finish(id);
                return;
            }
            if (!test(id, order++, workspace)) {
                store.finish(id);
                return;
            }
            if (!image(id, order++, workspace, dockerfilePath, contextPath, pushImage)) {
                store.finish(id);
                return;
            }
            push(id, order, pushImage);
            store.finish(id);
        } catch (RuntimeException ex) {
            log.warn("build {} failed: {}", id, sanitize(ex.getMessage()));
            store.fail(id, sanitize(ex.getMessage()));
        } catch (IOException ex) {
            log.warn("build {} failed: {}", id, sanitize(ex.getMessage()));
            store.fail(id, sanitize(ex.getMessage()));
        } finally {
            deleteRecursively(workspace);
        }
    }

    private boolean checkout(UUID id, int order, Path workspace) {
        DockerBuild build = store.require(id);
        String repository = ResourcePolicies.githubRepository(build.getRepositoryUrl());
        String branch = ResourcePolicies.branch(build.getBranch());
        if (!repository.startsWith("https://")) {
            store.addStep(id, order, "checkout", "FAILED", "Only HTTPS GitHub repositories are accepted");
            return false;
        }
        try (Git git = clone(repository, branch, workspace)) {
            store.addStep(id, order, "checkout", "SUCCEEDED", "Cloned " + repository + " branch " + branch);
            return git != null;
        } catch (RuntimeException | GitAPIException ex) {
            store.addStep(id, order, "checkout", "FAILED", sanitize(ex.getMessage()));
            return false;
        }
    }

    private Git clone(String repository, String branch, Path workspace) throws GitAPIException {
        var command = Git.cloneRepository()
                .setURI(repository)
                .setDirectory(workspace.toFile())
                .setDepth(1)
                .setCloneAllBranches(false)
                .setBranch("refs/heads/" + branch)
                .setBranchesToClone(java.util.List.of("refs/heads/" + branch))
                .setTimeout(120);
        String token = github.getToken();
        if (token != null && !token.isBlank()) {
            command.setCredentialsProvider(new UsernamePasswordCredentialsProvider("x-access-token", token));
        }
        return command.call();
    }

    private boolean test(UUID id, int order, Path workspace) {
        try {
            if (Files.isRegularFile(workspace.resolve("pom.xml"))) {
                return commandStep(id, order, workspace, FixedCommands.MAVEN_IMAGE, FixedCommands.MAVEN_TEST);
            }
            if (Files.isRegularFile(workspace.resolve("package.json"))) {
                return commandStep(id, order, workspace, FixedCommands.NPM_IMAGE, FixedCommands.NPM_TEST);
            }
            store.addStep(id, order, "test", "SKIPPED", "No pom.xml or package.json; tests skipped");
            return true;
        } catch (RuntimeException ex) {
            store.addStep(id, order, "test", "FAILED", sanitize(ex.getMessage()));
            return false;
        }
    }

    private boolean commandStep(UUID id, int order, Path workspace, String image, String[] command) {
        DockerGateway.ExecResult result = docker.runFixed(workspace, image, command);
        String status = result.exitCode() == 0 ? "SUCCEEDED" : "FAILED";
        store.addStep(id, order, "test", status, sanitize(result.output()));
        return result.exitCode() == 0;
    }

    private boolean image(UUID id, int order, Path workspace, String dockerfilePath, String contextPath, String pushImage) {
        DockerBuild build = store.require(id);
        try {
            DockerGateway.BuiltImage built = docker.buildImage(workspace, dockerfilePath, contextPath, pushImage, build.getApplicationId());
            int colon = pushImage.lastIndexOf(':');
            store.recordImage(id, pushImage.substring(0, colon), pushImage.substring(colon + 1), built.dockerImageId(), built.sizeBytes());
            store.addStep(id, order, "image", "SUCCEEDED", sanitize(built.log()));
            return true;
        } catch (DockerGateway.StepFailed ex) {
            store.addStep(id, order, "image", "FAILED", sanitize(ex.logText()));
            return false;
        } catch (RuntimeException ex) {
            store.addStep(id, order, "image", "FAILED", sanitize(ex.getMessage()));
            return false;
        }
    }

    private void push(UUID id, int order, String pushImage) {
        if (dockerProperties.getRegistry() == null || dockerProperties.getRegistry().isBlank()) {
            store.addStep(id, order, "push", "SKIPPED", "No registry configured");
            return;
        }
        try {
            String pushLog = docker.push(pushImage);
            store.addStep(id, order, "push", "SUCCEEDED", sanitize(pushLog));
        } catch (DockerGateway.StepFailed ex) {
            store.addStep(id, order, "push", "FAILED", sanitize(ex.logText()));
        } catch (RuntimeException ex) {
            store.addStep(id, order, "push", "FAILED", sanitize(ex.getMessage()));
        }
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "Build step failed";
        }
        String cleaned = message;
        String token = github.getToken();
        if (token != null && !token.isBlank()) {
            cleaned = cleaned.replace(token, "***");
        }
        return cleaned;
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    // The clone directory is removed on a best-effort basis after the job.
                }
            });
        } catch (IOException ex) {
            // Best-effort cleanup of the temporary clone.
        }
    }
}
