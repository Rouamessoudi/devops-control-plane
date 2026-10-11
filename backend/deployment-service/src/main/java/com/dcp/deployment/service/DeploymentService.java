package com.dcp.deployment.service;

import com.dcp.common.error.ApiException;
import com.dcp.common.policy.ResourcePolicies;
import com.dcp.common.security.JwtPrincipal;
import com.dcp.common.web.CurrentUser;
import com.dcp.common.web.InternalRestClient;
import com.dcp.common.web.PageResponse;
import com.dcp.deployment.api.CreateDeploymentRequest;
import com.dcp.deployment.api.DeploymentEventView;
import com.dcp.deployment.api.DeploymentView;
import com.dcp.deployment.api.InternalDeploymentRequest;
import com.dcp.deployment.client.BuildAccepted;
import com.dcp.deployment.client.BuildView;
import com.dcp.deployment.client.DockerBuildRequest;
import com.dcp.deployment.client.K8sApplyRequest;
import com.dcp.deployment.client.K8sDeploymentView;
import com.dcp.deployment.client.MonitoringTargetRequest;
import com.dcp.deployment.client.NotificationRequest;
import com.dcp.deployment.client.RemoteApplication;
import com.dcp.deployment.client.RemoteEnvVar;
import com.dcp.deployment.client.StatusPatch;
import com.dcp.deployment.domain.DeploymentEntity;
import com.dcp.deployment.domain.DeploymentEventEntity;
import com.dcp.deployment.domain.DeploymentStatus;
import com.dcp.deployment.domain.EnvironmentName;
import com.dcp.deployment.domain.TriggerType;
import com.dcp.deployment.repo.DeploymentEventRepository;
import com.dcp.deployment.repo.DeploymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
@Transactional
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(8);
    private static final DateTimeFormatter IMAGE_TAG_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final DeploymentRepository deployments;
    private final DeploymentEventRepository events;
    private final InternalRestClient applicationClient;
    private final InternalRestClient dockerClient;
    private final InternalRestClient kubernetesClient;
    private final InternalRestClient authClient;
    private final InternalRestClient monitoringClient;
    private final Executor deploymentExecutor;
    private final int maxReplicas;

    public DeploymentService(DeploymentRepository deployments,
                             DeploymentEventRepository events,
                             @Qualifier("applicationClient") InternalRestClient applicationClient,
                             @Qualifier("dockerClient") InternalRestClient dockerClient,
                             @Qualifier("kubernetesClient") InternalRestClient kubernetesClient,
                             @Qualifier("authClient") InternalRestClient authClient,
                             @Qualifier("monitoringClient") InternalRestClient monitoringClient,
                             @Qualifier("deploymentExecutor") Executor deploymentExecutor,
                             @Value("${dcp.kubernetes.max-replicas:5}") int maxReplicas) {
        this.deployments = deployments;
        this.events = events;
        this.applicationClient = applicationClient;
        this.dockerClient = dockerClient;
        this.kubernetesClient = kubernetesClient;
        this.authClient = authClient;
        this.monitoringClient = monitoringClient;
        this.deploymentExecutor = deploymentExecutor;
        this.maxReplicas = maxReplicas;
    }

    public DeploymentView createForUser(CreateDeploymentRequest request) {
        if (request == null || request.applicationId() == null) {
            throw ApiException.badRequest("applicationId is required");
        }
        int replicas = checkedReplicas(request.replicas());
        String imageTag = checkedTag(request.imageTag());
        TriggerType trigger = parseTrigger(request.trigger());
        JwtPrincipal principal = CurrentUser.require();
        return accept(principal.userId(), request.applicationId(), imageTag, replicas, trigger, null, null, false);
    }

    public DeploymentView createForInternal(InternalDeploymentRequest request) {
        if (request == null || request.applicationId() == null || request.requestedBy() == null) {
            throw ApiException.badRequest("applicationId and requestedBy are required");
        }
        int replicas = checkedReplicas(request.replicas());
        String imageTag = checkedTag(request.imageTag());
        TriggerType trigger = parseTrigger(request.trigger());
        return accept(request.requestedBy(), request.applicationId(), imageTag, replicas, trigger, request.pipelineId(), null, true);
    }

    public DeploymentView rollback(UUID id) {
        JwtPrincipal principal = CurrentUser.require();
        DeploymentEntity current = requireDeployment(id);
        assertOwner(principal, current.getOwnerId());
        DeploymentEntity previous = previousSuccessfulImage(current);
        return accept(principal.userId(), current.getApplicationId(), previous.getImageTag(), previous.getReplicas(),
                TriggerType.ROLLBACK, null, previous.getImageRepository(), false);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeploymentView> list(UUID applicationId, int page, int size) {
        JwtPrincipal principal = CurrentUser.require();
        Pageable pageable = pageable(page, size);
        Page<DeploymentEntity> result;
        if (principal.hasRole("ADMIN")) {
            result = applicationId == null
                    ? deployments.findAll(pageable)
                    : deployments.findByApplicationId(applicationId, pageable);
        } else if (applicationId == null) {
            result = deployments.findByOwnerId(principal.userId(), pageable);
        } else {
            result = deployments.findByOwnerIdAndApplicationId(principal.userId(), applicationId, pageable);
        }
        return toPage(result, result.getContent().stream().map(DeploymentService::toView).toList());
    }

    @Transactional(readOnly = true)
    public DeploymentView get(UUID id) {
        JwtPrincipal principal = CurrentUser.require();
        DeploymentEntity entity = requireDeployment(id);
        assertOwner(principal, entity.getOwnerId());
        return toView(entity);
    }

    @Transactional(readOnly = true)
    public DeploymentView getInternal(UUID id) {
        return toView(requireDeployment(id));
    }

    @Transactional(readOnly = true)
    public List<DeploymentView> revisions(UUID id) {
        JwtPrincipal principal = CurrentUser.require();
        DeploymentEntity current = requireDeployment(id);
        assertOwner(principal, current.getOwnerId());
        return deployments.findByApplicationIdOrderByCreatedAtAsc(current.getApplicationId()).stream()
                .map(DeploymentService::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<DeploymentEventView> auditEvents(int page, int size) {
        JwtPrincipal principal = CurrentUser.require();
        Pageable pageable = pageable(page, size);
        Page<DeploymentEventEntity> result = principal.hasRole("ADMIN")
                ? events.findAll(pageable)
                : events.findVisibleToOwner(principal.userId(), pageable);
        return toPage(result, result.getContent().stream().map(DeploymentService::toEvent).toList());
    }

    void runWorkflow(UUID deploymentId, boolean internalCall) {
        try {
            execute(deploymentId, internalCall);
        } catch (Exception ex) {
            log.error("Deployment {} failed", deploymentId, ex);
            markFailed(deploymentId, ex);
        }
    }

    private DeploymentView accept(UUID requestedBy,
                                  UUID applicationId,
                                  String imageTag,
                                  int replicas,
                                  TriggerType trigger,
                                  UUID pipelineId,
                                  String imageRepository,
                                  boolean internalCall) {
        ResourcePolicies.replicas(replicas, maxReplicas);
        RemoteApplication application = applicationClient.get("/internal/applications/" + applicationId, RemoteApplication.class);
        if (!internalCall) {
            assertOwner(CurrentUser.require(), application.ownerId());
        }
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setApplicationId(applicationId);
        deployment.setOwnerId(application.ownerId());
        deployment.setRequestedBy(requestedBy);
        deployment.setImageRepository(imageRepository);
        deployment.setImageTag(imageTag);
        deployment.setReplicas(replicas);
        deployment.setEnvironmentName(parseEnvironment(application.environment()));
        deployment.setTriggerType(trigger);
        deployment.setStatus(DeploymentStatus.PENDING);
        deployment.onCreate();
        deployments.save(deployment);
        String message = pipelineId == null ? "Deployment requested" : "Deployment requested by pipeline " + pipelineId;
        record(deployment, "REQUESTED", message);
        submit(deployment.getId(), internalCall);
        log.info("Accepted deployment {} for application {}", deployment.getId(), applicationId);
        return toView(deployment);
    }

    private void execute(UUID deploymentId, boolean internalCall) {
        DeploymentEntity deployment = requireDeployment(deploymentId);
        RemoteApplication application = applicationClient.get(
                "/internal/applications/" + deployment.getApplicationId(), RemoteApplication.class);
        if (!internalCall) {
            JwtPrincipal principal = CurrentUser.require();
            if (!principal.hasRole("ADMIN") && !principal.userId().equals(application.ownerId())) {
                throw ApiException.forbidden("You cannot deploy this application");
            }
        }
        ResourcePolicies.replicas(deployment.getReplicas(), maxReplicas);
        String tag = deployment.getImageTag();
        if (tag == null || tag.isBlank()) {
            tag = IMAGE_TAG_FORMAT.format(Instant.now());
        }
        deployment.setImageTag(ResourcePolicies.imageTag(tag));
        deployment.setStatus(DeploymentStatus.RUNNING);
        deployment.setStartedAt(Instant.now());
        deployments.save(deployment);
        record(deployment, "RUNNING", "Deployment started");

        applicationClient.patch("/internal/applications/" + application.id() + "/status",
                new StatusPatch("DEPLOYING"), RemoteApplication.class);

        String imageRepository;
        String imageTag;
        String fullImage;
        if (skipDockerBuild(deployment)) {
            imageRepository = deployment.getImageRepository();
            imageTag = deployment.getImageTag();
            fullImage = imageRepository + ":" + imageTag;
        } else {
            BuildAccepted accepted = dockerClient.post("/internal/docker/builds", new DockerBuildRequest(
                    application.id(),
                    deployment.getRequestedBy(),
                    application.gitRepositoryUrl(),
                    application.branch(),
                    application.dockerfilePath(),
                    application.contextPath(),
                    deployment.getImageTag()
            ), BuildAccepted.class);
            BuildView build = awaitBuild(accepted.id());
            imageRepository = build.imageName();
            imageTag = build.imageTag();
            fullImage = build.fullImage() == null || build.fullImage().isBlank()
                    ? imageRepository + ":" + imageTag
                    : build.fullImage();
        }

        RemoteEnvVar[] env = applicationClient.get(
                "/internal/applications/" + application.id() + "/env", RemoteEnvVar[].class);
        List<RemoteEnvVar> variables = env == null ? List.of() : Arrays.asList(env);
        String requestedName = "dcp-" + application.name();
        K8sDeploymentView applied = kubernetesClient.post("/internal/kubernetes/deployments", new K8sApplyRequest(
                requestedName,
                fullImage,
                deployment.getReplicas(),
                application.containerPort(),
                deployment.getRequestedBy(),
                application.id(),
                variables
        ), K8sDeploymentView.class);

        deployment.setStatus(DeploymentStatus.SUCCEEDED);
        deployment.setK8sRevision(applied.revision());
        deployment.setK8sDeploymentName(applied.name() == null || applied.name().isBlank() ? requestedName : applied.name());
        deployment.setImageRepository(imageRepository);
        deployment.setImageTag(imageTag);
        deployment.setErrorMessage(null);
        deployment.setFinishedAt(Instant.now());
        deployments.save(deployment);
        record(deployment, "SUCCEEDED", "Deployment succeeded");

        applicationClient.patch("/internal/applications/" + application.id() + "/status",
                new StatusPatch("RUNNING"), RemoteApplication.class);

        UUID ownerId = application.ownerId();
        String liveName = deployment.getK8sDeploymentName();
        String deployedImage = fullImage;
        bestEffort("monitoring target", () -> monitoringClient.post("/internal/monitoring/targets",
                new MonitoringTargetRequest(application.id(), ownerId, liveName, "dcp")));
        bestEffort("notification", () -> authClient.post("/internal/notifications",
                new NotificationRequest(ownerId, "Deployment succeeded",
                        "Application " + application.name() + " is running " + deployedImage)));
    }

    private void markFailed(UUID deploymentId, Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        try {
            DeploymentEntity deployment = deployments.findById(deploymentId).orElse(null);
            if (deployment == null) {
                log.error("Deployment {} failed before it could be loaded", deploymentId);
                return;
            }
            deployment.setStatus(DeploymentStatus.FAILED);
            deployment.setErrorMessage(message);
            deployment.setFinishedAt(Instant.now());
            deployments.save(deployment);
            record(deployment, "FAILED", message);
            UUID applicationId = deployment.getApplicationId();
            UUID ownerId = deployment.getOwnerId();
            String notice = message;
            bestEffort("application status", () -> applicationClient.patch(
                    "/internal/applications/" + applicationId + "/status",
                    new StatusPatch("FAILED"),
                    RemoteApplication.class));
            bestEffort("notification", () -> authClient.post("/internal/notifications",
                    new NotificationRequest(ownerId, "Deployment failed", "Deployment failed: " + abbreviate(notice, 300))));
        } catch (RuntimeException inner) {
            log.error("Unable to record failure for deployment {}", deploymentId, inner);
        }
    }

    private BuildView awaitBuild(UUID buildId) {
        Instant deadline = Instant.now().plus(BUILD_TIMEOUT);
        while (true) {
            BuildView build = dockerClient.get("/internal/docker/builds/" + buildId, BuildView.class);
            if (build == null) {
                throw ApiException.unavailable("Docker build response was empty");
            }
            if (isTerminal(build.status())) {
                if (!"SUCCEEDED".equals(build.status())) {
                    String reason = build.errorMessage();
                    if (reason == null || reason.isBlank()) {
                        reason = "Docker build " + build.status();
                    }
                    throw ApiException.unavailable(reason);
                }
                return build;
            }
            if (!Instant.now().isBefore(deadline)) {
                throw ApiException.unavailable("Docker build did not finish within 8 minutes");
            }
            pauseBetweenBuildPolls();
        }
    }

    void pauseBetweenBuildPolls() {
        try {
            Thread.sleep(Duration.ofSeconds(2).toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ApiException.unavailable("Deployment was interrupted");
        }
    }

    private static boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status) || "SKIPPED".equals(status);
    }

    private static boolean skipDockerBuild(DeploymentEntity deployment) {
        return deployment.getTriggerType() == TriggerType.ROLLBACK
                && deployment.getImageRepository() != null
                && !deployment.getImageRepository().isBlank();
    }

    private DeploymentEntity previousSuccessfulImage(DeploymentEntity current) {
        String currentImage = fullImage(current.getImageRepository(), current.getImageTag());
        List<DeploymentEntity> earlier = deployments.findByApplicationIdAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(
                current.getApplicationId(), DeploymentStatus.SUCCEEDED, current.getCreatedAt());
        for (DeploymentEntity candidate : earlier) {
            String image = fullImage(candidate.getImageRepository(), candidate.getImageTag());
            if (image != null && !image.equals(currentImage)) {
                return candidate;
            }
        }
        throw ApiException.conflict("No previous successful deployment with a different image exists");
    }

    private void submit(UUID deploymentId, boolean internalCall) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Runnable task = () -> {
            try {
                if (authentication != null) {
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);
                }
                runWorkflow(deploymentId, internalCall);
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deploymentExecutor.execute(task);
                }
            });
        } else {
            deploymentExecutor.execute(task);
        }
    }

    private void record(DeploymentEntity deployment, String action, String message) {
        DeploymentEventEntity event = new DeploymentEventEntity();
        event.setDeploymentId(deployment.getId());
        event.setActorId(deployment.getRequestedBy());
        event.setAction(action);
        event.setMessage(abbreviate(message, 500));
        event.onCreate();
        events.save(event);
    }

    private void bestEffort(String action, Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException ex) {
            log.warn("{} failed: {}", action, ex.getMessage());
        }
    }

    private DeploymentEntity requireDeployment(UUID id) {
        return deployments.findById(id).orElseThrow(() -> ApiException.notFound("Deployment not found"));
    }

    private static void assertOwner(JwtPrincipal principal, UUID ownerId) {
        if (principal.hasRole("ADMIN") || principal.userId().equals(ownerId)) {
            return;
        }
        throw ApiException.forbidden("You cannot access this deployment");
    }

    private int checkedReplicas(Integer requested) {
        int replicas = requested == null ? 1 : requested;
        ResourcePolicies.replicas(replicas, maxReplicas);
        return replicas;
    }

    private static String checkedTag(String imageTag) {
        if (imageTag == null || imageTag.isBlank()) {
            return null;
        }
        return ResourcePolicies.imageTag(imageTag);
    }

    private static TriggerType parseTrigger(String trigger) {
        if (trigger == null || trigger.isBlank()) {
            return TriggerType.MANUAL;
        }
        try {
            return TriggerType.valueOf(trigger.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown deployment trigger");
        }
    }

    private static EnvironmentName parseEnvironment(String environment) {
        if (environment == null || environment.isBlank()) {
            throw ApiException.badRequest("Application environment is required");
        }
        try {
            return EnvironmentName.valueOf(environment.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown application environment");
        }
    }

    private static DeploymentView toView(DeploymentEntity entity) {
        return new DeploymentView(
                entity.getId(),
                entity.getApplicationId(),
                entity.getRequestedBy(),
                entity.getImageRepository(),
                entity.getImageTag(),
                fullImage(entity.getImageRepository(), entity.getImageTag()),
                entity.getReplicas(),
                entity.getEnvironmentName(),
                entity.getTriggerType(),
                entity.getStatus(),
                entity.getK8sDeploymentName(),
                entity.getK8sRevision(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt()
        );
    }

    private static DeploymentEventView toEvent(DeploymentEventEntity entity) {
        return new DeploymentEventView(
                entity.getId(),
                entity.getDeploymentId(),
                entity.getActorId(),
                entity.getAction(),
                entity.getMessage(),
                entity.getCreatedAt()
        );
    }

    static String fullImage(String repository, String tag) {
        if (repository == null || tag == null || repository.isBlank() || tag.isBlank()) {
            return null;
        }
        return repository + ":" + tag;
    }

    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private static <T> PageResponse<T> toPage(Page<?> page, List<T> items) {
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
