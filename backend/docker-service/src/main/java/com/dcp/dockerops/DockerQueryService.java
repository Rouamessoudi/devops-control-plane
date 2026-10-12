package com.dcp.dockerops;

import com.dcp.common.error.ApiException;
import com.dcp.common.security.JwtPrincipal;
import com.dcp.common.web.CurrentUser;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class DockerQueryService {

    private final DockerBuildRepository builds;
    private final DockerBuildStepRepository steps;
    private final ManagedImageRepository images;
    private final DockerGateway docker;

    public DockerQueryService(DockerBuildRepository builds,
                              DockerBuildStepRepository steps,
                              ManagedImageRepository images,
                              DockerGateway docker) {
        this.builds = builds;
        this.steps = steps;
        this.images = images;
        this.docker = docker;
    }

    @Transactional(readOnly = true)
    public DockerApi.BuildView build(UUID id, boolean internal) {
        DockerBuild row = builds.findById(id).orElseThrow(() -> ApiException.notFound("Build not found"));
        if (!internal) {
            JwtPrincipal user = CurrentUser.require();
            if (!user.hasRole("ADMIN") && !user.userId().equals(row.getRequestedBy())) {
                throw ApiException.notFound("Build not found");
            }
        }
        return toView(row);
    }

    @Transactional(readOnly = true)
    public List<DockerApi.BuildView> builds(UUID applicationId) {
        JwtPrincipal user = CurrentUser.require();
        List<DockerBuild> rows;
        if (user.hasRole("ADMIN")) {
            rows = applicationId == null
                    ? builds.findAllByOrderByStartedAtDesc()
                    : builds.findByApplicationIdOrderByStartedAtDesc(applicationId);
        } else if (applicationId == null) {
            rows = builds.findByRequestedByOrderByStartedAtDesc(user.userId());
        } else {
            rows = builds.findByApplicationIdAndRequestedByOrderByStartedAtDesc(applicationId, user.userId());
        }
        return rows.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<DockerApi.BuildAuditEvent> audit() {
        JwtPrincipal user = CurrentUser.require();
        List<DockerBuild> rows = user.hasRole("ADMIN")
                ? builds.findAllByOrderByStartedAtDesc()
                : builds.findByRequestedByOrderByStartedAtDesc(user.userId());
        return rows.stream().map(this::toAudit).toList();
    }

    @Transactional(readOnly = true)
    public DockerApi.ImageListing images() {
        List<DockerApi.ImageRow> rows = new ArrayList<>();
        for (ManagedImage image : images.findAllByOrderByCreatedAtDesc()) {
            rows.add(new DockerApi.ImageRow(
                    image.getId(),
                    image.getBuildId(),
                    image.getRepository(),
                    image.getTag(),
                    image.getDockerImageId(),
                    image.getSizeBytes(),
                    image.getCreatedAt(),
                    "database"));
        }
        try {
            for (Image image : docker.listManagedImages()) {
                rows.addAll(daemonRows(image));
            }
            return new DockerApi.ImageListing(rows, null);
        } catch (ApiException ex) {
            if (ex.status() == 503) {
                return new DockerApi.ImageListing(rows, ex.getMessage());
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (DockerGateway.unavailable(ex)) {
                return new DockerApi.ImageListing(rows, "Docker daemon is unavailable");
            }
            throw ex;
        }
    }

    public List<DockerApi.ContainerView> containers() {
        CurrentUser.require();
        List<Container> live = docker.listManagedContainers();
        if (live == null || live.isEmpty()) {
            return List.of();
        }
        List<DockerApi.ContainerView> views = new ArrayList<>();
        for (Container container : live) {
            views.add(new DockerApi.ContainerView(
                    container.getId(),
                    container.getImage(),
                    container.getState(),
                    container.getStatus(),
                    container.getNames() == null ? List.of() : Arrays.asList(container.getNames()),
                    container.getCreated() == null ? null : Instant.ofEpochSecond(container.getCreated())));
        }
        return views;
    }

    public String logs(String id) {
        CurrentUser.require();
        return docker.containerLogs(id);
    }

    private DockerApi.BuildView toView(DockerBuild row) {
        List<DockerApi.StepView> stepViews = steps.findByBuildIdOrderByStepOrderAsc(row.getId()).stream()
                .map(step -> new DockerApi.StepView(step.getName(), step.getStatus(), step.getLogText()))
                .toList();
        return new DockerApi.BuildView(
                row.getId(),
                row.getApplicationId(),
                row.getStatus(),
                row.getImageName(),
                row.getImageTag(),
                row.getFullImage(),
                row.getLogText(),
                row.getErrorMessage(),
                row.getStartedAt(),
                row.getFinishedAt(),
                stepViews);
    }

    private DockerApi.BuildAuditEvent toAudit(DockerBuild row) {
        return new DockerApi.BuildAuditEvent(
                row.getId(),
                row.getApplicationId(),
                row.getRequestedBy(),
                row.getRepositoryUrl(),
                row.getBranch(),
                row.getImageName(),
                row.getImageTag(),
                row.getFullImage(),
                row.getStatus(),
                row.getLogText(),
                row.getErrorMessage(),
                row.getStartedAt(),
                row.getFinishedAt());
    }

    private static List<DockerApi.ImageRow> daemonRows(Image image) {
        String[] tags = image.getRepoTags();
        Instant created = image.getCreated() == null ? null : Instant.ofEpochSecond(image.getCreated());
        if (tags == null || tags.length == 0) {
            return List.of(new DockerApi.ImageRow(null, null, null, null, image.getId(), image.getSize(), created, "daemon"));
        }
        List<DockerApi.ImageRow> rows = new ArrayList<>();
        for (String repoTag : tags) {
            String repository = repoTag;
            String tag = null;
            int slash = repoTag.lastIndexOf('/');
            int colon = repoTag.lastIndexOf(':');
            if (colon > slash) {
                repository = repoTag.substring(0, colon);
                tag = repoTag.substring(colon + 1);
            }
            rows.add(new DockerApi.ImageRow(null, null, repository, tag, image.getId(), image.getSize(), created, "daemon"));
        }
        return rows;
    }
}
