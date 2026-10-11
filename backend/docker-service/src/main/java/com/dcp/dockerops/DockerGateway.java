package com.dcp.dockerops;

import com.dcp.common.error.ApiException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.core.command.PushImageResultCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
class DockerGateway {

    private static final Logger log = LoggerFactory.getLogger(DockerGateway.class);
    private static final Pattern CONTAINER_ID = Pattern.compile("^[a-f0-9]{12,64}$");

    private final DockerClient dockerClient;

    DockerGateway(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    List<Image> listManagedImages() {
        try {
            return dockerClient.listImagesCmd()
                    .withLabelFilter(Map.of("dcp.managed", "true"))
                    .withShowAll(true)
                    .exec();
        } catch (RuntimeException ex) {
            throw map(ex);
        }
    }

    List<Container> listManagedContainers() {
        try {
            return dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withLabelFilter(Map.of("dcp.managed", "true"))
                    .exec();
        } catch (RuntimeException ex) {
            throw map(ex);
        }
    }

    String containerLogs(String id) {
        if (id == null || !CONTAINER_ID.matcher(id).matches()) {
            throw ApiException.notFound("Container not found");
        }
        try {
            InspectContainerResponse inspected = dockerClient.inspectContainerCmd(id).exec();
            Map<String, String> labels = inspected.getConfig() == null ? null : inspected.getConfig().getLabels();
            if (labels == null || !"true".equals(labels.get("dcp.managed"))) {
                throw ApiException.notFound("Container not found");
            }
            StringBuilder output = new StringBuilder();
            dockerClient.logContainerCmd(id)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(500)
                    .exec(frames(output))
                    .awaitCompletion();
            return output.toString();
        } catch (ApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ApiException.unavailable("Docker daemon is unavailable");
        } catch (RuntimeException ex) {
            throw map(ex);
        }
    }

    ExecResult runFixed(Path workspace, String image, String[] command) {
        FixedCommands.check(image, command);
        String containerId = null;
        try {
            CreateContainerResponse created = dockerClient.createContainerCmd(image)
                    .withCmd("sleep", "600")
                    .withLabels(Map.of("dcp.managed", "true"))
                    .exec();
            containerId = created.getId();
            dockerClient.startContainerCmd(containerId).exec();
            dockerClient.copyArchiveToContainerCmd(containerId)
                    .withHostResource(workspace.toString())
                    .withRemotePath("/workspace")
                    .withDirChildrenOnly(true)
                    .exec();
            ExecCreateCmdResponse exec = dockerClient.execCreateCmd(containerId)
                    .withCmd(Arrays.copyOf(command, command.length))
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();
            StringBuilder output = new StringBuilder();
            dockerClient.execStartCmd(exec.getId())
                    .exec(frames(output))
                    .awaitCompletion(8, TimeUnit.MINUTES);
            InspectExecResponse inspected = dockerClient.inspectExecCmd(exec.getId()).exec();
            Long exit = inspected.getExitCodeLong();
            int code = exit == null ? 1 : exit.intValue();
            Texts.appendCapped(output, "\nexit=" + code);
            return new ExecResult(code, output.toString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ApiException.unavailable("Docker daemon is unavailable");
        } catch (RuntimeException ex) {
            throw map(ex);
        } finally {
            if (containerId != null) {
                try {
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                } catch (RuntimeException ex) {
                    log.warn("failed to remove test container {}", containerId);
                }
            }
        }
    }

    BuiltImage buildImage(Path workspace, String dockerfilePath, String contextPath, String fullPushImage, UUID applicationId) {
        Path context = WorkspacePaths.resolveInside(workspace, contextPath == null || contextPath.isBlank() ? "." : contextPath);
        Path dockerfile = WorkspacePaths.resolveInside(workspace, dockerfilePath == null || dockerfilePath.isBlank() ? "Dockerfile" : dockerfilePath);
        if (!dockerfile.startsWith(context)) {
            throw ApiException.badRequest("Dockerfile must stay inside the build context");
        }
        if (!Files.isRegularFile(dockerfile)) {
            throw ApiException.badRequest("Dockerfile was not found in the workspace");
        }
        ImageNames.requireOwned(fullPushImage);
        CollectingBuild callback = new CollectingBuild();
        try {
            String imageId = dockerClient.buildImageCmd(dockerfile.toFile())
                    .withBaseDirectory(context.toFile())
                    .withTags(Set.of(fullPushImage))
                    .withLabels(Map.of(
                            "dcp.managed", "true",
                            "dcp.application-id", applicationId.toString()))
                    .exec(callback)
                    .awaitImageId(10, TimeUnit.MINUTES);
            Long size = null;
            String dockerId = imageId;
            try {
                InspectImageResponse info = dockerClient.inspectImageCmd(imageId).exec();
                size = info.getSize();
                if (info.getId() != null) {
                    dockerId = info.getId();
                }
            } catch (RuntimeException ex) {
                Texts.appendCapped(callback.log, "\nimage inspect failed: " + ex.getMessage());
            }
            return new BuiltImage(dockerId, size, callback.log.toString());
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            String message = callback.log + "\n" + (ex.getMessage() == null ? "image build failed" : ex.getMessage());
            throw new StepFailed(message, ex);
        }
    }

    String push(String fullPushImage) {
        ImageNames.requireOwned(fullPushImage);
        int colon = fullPushImage.lastIndexOf(':');
        String repository = fullPushImage.substring(0, colon);
        String tag = fullPushImage.substring(colon + 1);
        CollectingPush callback = new CollectingPush();
        try {
            dockerClient.pushImageCmd(repository)
                    .withTag(tag)
                    .exec(callback)
                    .awaitCompletion(10, TimeUnit.MINUTES);
            return callback.log.toString();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ApiException.unavailable("Docker daemon is unavailable");
        } catch (RuntimeException ex) {
            String message = callback.log + "\n" + (ex.getMessage() == null ? "image push failed" : ex.getMessage());
            throw new StepFailed(message, ex);
        }
    }

    static boolean unavailable(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof java.net.ConnectException
                    || current instanceof java.net.UnknownHostException
                    || current instanceof java.net.SocketException
                    || current instanceof java.nio.channels.ClosedChannelException) {
                return true;
            }
            String name = current.getClass().getName();
            if (name.contains("HttpHostConnectException") || name.contains("ConnectTimeoutException") || name.contains("ConnectionClosedException")) {
                return true;
            }
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (message.contains("docker_engine") || message.contains("npipe") || message.contains("failed to connect")
                    || message.contains("connection refused") || message.contains("no such file")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static RuntimeException map(RuntimeException ex) {
        if (ex instanceof ApiException || ex instanceof StepFailed) {
            return ex;
        }
        if (ex instanceof NotFoundException) {
            return ApiException.notFound("Docker resource not found");
        }
        if (unavailable(ex)) {
            return ApiException.unavailable("Docker daemon is unavailable");
        }
        return ex;
    }

    private static ResultCallback.Adapter<Frame> frames(StringBuilder output) {
        return new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                if (frame.getPayload() != null && frame.getPayload().length > 0) {
                    Texts.appendCapped(output, new String(frame.getPayload(), StandardCharsets.UTF_8));
                }
            }
        };
    }

    record ExecResult(int exitCode, String output) {
    }

    record BuiltImage(String dockerImageId, Long sizeBytes, String log) {
    }

    static final class StepFailed extends RuntimeException {
        private final String logText;

        StepFailed(String logText, Throwable cause) {
            super(cause);
            this.logText = logText == null ? "" : logText;
        }

        String logText() {
            return logText;
        }
    }

    private static final class CollectingBuild extends BuildImageResultCallback {
        private final StringBuilder log = new StringBuilder();

        @Override
        public void onNext(BuildResponseItem item) {
            if (item.getStream() != null) {
                Texts.appendCapped(log, item.getStream());
            }
            if (item.getError() != null) {
                Texts.appendCapped(log, item.getError());
            }
            super.onNext(item);
        }
    }

    private static final class CollectingPush extends PushImageResultCallback {
        private final StringBuilder log = new StringBuilder();

        @Override
        public void onNext(PushResponseItem item) {
            if (item.getStatus() != null) {
                Texts.appendCapped(log, item.getStatus());
                Texts.appendCapped(log, "\n");
            }
            if (item.getError() != null) {
                Texts.appendCapped(log, item.getError());
                Texts.appendCapped(log, "\n");
            }
            super.onNext(item);
        }
    }
}
