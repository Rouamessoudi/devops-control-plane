package com.dcp.application.service;

import com.dcp.application.api.ApplicationRequest;
import com.dcp.application.api.ApplicationView;
import com.dcp.application.api.EnvVarRequest;
import com.dcp.application.api.EnvVarView;
import com.dcp.application.api.HistoryView;
import com.dcp.application.api.InternalEnvVarView;
import com.dcp.application.api.StatusUpdateRequest;
import com.dcp.application.domain.AppEnvVarEntity;
import com.dcp.application.domain.ApplicationEntity;
import com.dcp.application.domain.ApplicationHistoryEntity;
import com.dcp.application.domain.ApplicationStatus;
import com.dcp.application.domain.EnvironmentName;
import com.dcp.application.repo.AppEnvVarRepository;
import com.dcp.application.repo.ApplicationHistoryRepository;
import com.dcp.application.repo.ApplicationRepository;
import com.dcp.common.crypto.SecretBox;
import com.dcp.common.error.ApiException;
import com.dcp.common.policy.ResourcePolicies;
import com.dcp.common.security.JwtPrincipal;
import com.dcp.common.web.CurrentUser;
import com.dcp.common.web.PageResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ApplicationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String MASK = "********";

    private final ApplicationRepository applications;
    private final AppEnvVarRepository envVars;
    private final ApplicationHistoryRepository history;
    private final SecretBox secretBox;

    public ApplicationService(ApplicationRepository applications,
                              AppEnvVarRepository envVars,
                              ApplicationHistoryRepository history,
                              SecretBox secretBox) {
        this.applications = applications;
        this.envVars = envVars;
        this.history = history;
        this.secretBox = secretBox;
    }

    public ApplicationView create(ApplicationRequest request) {
        JwtPrincipal principal = CurrentUser.require();
        Normalized normalized = normalize(request);
        ensureNameAvailable(principal.userId(), normalized.name(), null);
        ApplicationEntity entity = new ApplicationEntity();
        entity.setOwnerId(principal.userId());
        entity.setStatus(ApplicationStatus.CREATED);
        apply(entity, normalized);
        entity.onCreate();
        ApplicationEntity saved = saveApplication(entity);
        record(saved.getId(), principal.userId(), "CREATED", "Application created");
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationView> list(int page, int size, String status) {
        JwtPrincipal principal = CurrentUser.require();
        ApplicationStatus parsed = parseOptionalStatus(status);
        Pageable pageable = pageable(page, size);
        Page<ApplicationEntity> result;
        if (principal.hasRole("ADMIN")) {
            result = parsed == null ? applications.findAll(pageable) : applications.findByStatus(parsed, pageable);
        } else if (parsed == null) {
            result = applications.findByOwnerId(principal.userId(), pageable);
        } else {
            result = applications.findByOwnerIdAndStatus(principal.userId(), parsed, pageable);
        }
        return toPage(result, result.getContent().stream().map(ApplicationService::toView).toList());
    }

    @Transactional(readOnly = true)
    public ApplicationView get(UUID id) {
        return toView(requireAccessible(id));
    }

    public ApplicationView update(UUID id, ApplicationRequest request) {
        JwtPrincipal principal = CurrentUser.require();
        ApplicationEntity entity = requireAccessible(id);
        Normalized normalized = normalize(request);
        ensureNameAvailable(entity.getOwnerId(), normalized.name(), entity.getId());
        apply(entity, normalized);
        entity.setUpdatedAt(Instant.now());
        ApplicationEntity saved = saveApplication(entity);
        record(saved.getId(), principal.userId(), "UPDATED", "Application updated");
        return toView(saved);
    }

    public void delete(UUID id) {
        ApplicationEntity entity = requireAccessible(id);
        applications.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<HistoryView> history(UUID id) {
        requireAccessible(id);
        return history.findByApplicationIdOrderByCreatedAtAsc(id).stream().map(ApplicationService::toHistory).toList();
    }

    public EnvVarView addEnv(UUID id, EnvVarRequest request) {
        JwtPrincipal principal = CurrentUser.require();
        ApplicationEntity application = requireAccessible(id);
        if (request == null) {
            throw ApiException.badRequest("Environment variable is required");
        }
        String key = ResourcePolicies.envKey(request.key());
        if (request.value() == null) {
            throw ApiException.badRequest("Environment variable value is required");
        }
        boolean secret = Boolean.TRUE.equals(request.secret());
        String ciphertext = secretBox.encrypt(request.value());
        if (ciphertext.length() > 2000) {
            throw ApiException.badRequest("Environment value is too long");
        }
        AppEnvVarEntity entity = envVars.findByApplicationIdAndEnvKey(application.getId(), key).orElseGet(AppEnvVarEntity::new);
        boolean created = entity.getId() == null;
        entity.setApplicationId(application.getId());
        entity.setEnvKey(key);
        entity.setValueCiphertext(ciphertext);
        entity.setSecretFlag(secret);
        if (created) {
            entity.onCreate();
        }
        AppEnvVarEntity saved = saveEnv(entity);
        record(application.getId(), principal.userId(), created ? "ENV_ADDED" : "ENV_UPDATED",
                "Environment variable " + key + (created ? " added" : " updated"));
        return toUserEnv(saved);
    }

    @Transactional(readOnly = true)
    public List<EnvVarView> listEnv(UUID id) {
        requireAccessible(id);
        return envVars.findByApplicationIdOrderByEnvKeyAsc(id).stream().map(this::toUserEnv).toList();
    }

    public void deleteEnv(UUID id, UUID envId) {
        JwtPrincipal principal = CurrentUser.require();
        requireAccessible(id);
        AppEnvVarEntity entity = envVars.findByIdAndApplicationId(envId, id)
                .orElseThrow(() -> ApiException.notFound("Environment variable not found"));
        String key = entity.getEnvKey();
        envVars.delete(entity);
        record(id, principal.userId(), "ENV_REMOVED", "Environment variable " + key + " removed");
    }

    @Transactional(readOnly = true)
    public PageResponse<HistoryView> auditEvents(int page, int size) {
        JwtPrincipal principal = CurrentUser.require();
        Pageable pageable = pageable(page, size);
        Page<ApplicationHistoryEntity> result = principal.hasRole("ADMIN")
                ? history.findAll(pageable)
                : history.findVisibleToOwner(principal.userId(), pageable);
        return toPage(result, result.getContent().stream().map(ApplicationService::toHistory).toList());
    }

    @Transactional(readOnly = true)
    public ApplicationView getInternal(UUID id) {
        return toView(requireApplication(id));
    }

    @Transactional(readOnly = true)
    public List<InternalEnvVarView> listInternalEnv(UUID id) {
        requireApplication(id);
        return envVars.findByApplicationIdOrderByEnvKeyAsc(id).stream()
                .map(entity -> new InternalEnvVarView(entity.getEnvKey(), secretBox.decrypt(entity.getValueCiphertext()), entity.isSecretFlag()))
                .toList();
    }

    public ApplicationView updateStatus(UUID id, StatusUpdateRequest request) {
        if (request == null || request.status() == null || request.status().isBlank()) {
            throw ApiException.badRequest("Status is required");
        }
        ApplicationStatus status;
        try {
            status = ApplicationStatus.valueOf(request.status().trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown application status");
        }
        ApplicationEntity entity = requireApplication(id);
        entity.setStatus(status);
        entity.setUpdatedAt(Instant.now());
        ApplicationEntity saved = applications.save(entity);
        record(saved.getId(), null, "STATUS_CHANGED", "Status changed to " + status.name());
        return toView(saved);
    }

    private ApplicationEntity requireAccessible(UUID id) {
        ApplicationEntity entity = requireApplication(id);
        JwtPrincipal principal = CurrentUser.require();
        if (!principal.hasRole("ADMIN") && !principal.userId().equals(entity.getOwnerId())) {
            throw ApiException.forbidden("You cannot access this application");
        }
        return entity;
    }

    private ApplicationEntity requireApplication(UUID id) {
        return applications.findById(id).orElseThrow(() -> ApiException.notFound("Application not found"));
    }

    private void ensureNameAvailable(UUID ownerId, String name, UUID currentId) {
        applications.findByOwnerIdAndName(ownerId, name)
                .filter(existing -> currentId == null || !currentId.equals(existing.getId()))
                .ifPresent(existing -> {
                    throw ApiException.conflict("An application with this name already exists");
                });
    }

    private ApplicationEntity saveApplication(ApplicationEntity entity) {
        try {
            return applications.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("An application with this name already exists");
        }
    }

    private AppEnvVarEntity saveEnv(AppEnvVarEntity entity) {
        try {
            return envVars.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("Environment variable already exists");
        }
    }

    private void record(UUID applicationId, UUID actorId, String action, String summary) {
        ApplicationHistoryEntity event = new ApplicationHistoryEntity();
        event.setApplicationId(applicationId);
        event.setActorId(actorId);
        event.setAction(action);
        event.setSummary(abbreviate(summary, 500));
        event.onCreate();
        history.save(event);
    }

    private EnvVarView toUserEnv(AppEnvVarEntity entity) {
        String value = entity.isSecretFlag() ? MASK : secretBox.decrypt(entity.getValueCiphertext());
        return new EnvVarView(entity.getId(), entity.getEnvKey(), entity.isSecretFlag(), value);
    }

    private static void apply(ApplicationEntity entity, Normalized normalized) {
        entity.setName(normalized.name());
        entity.setDescription(normalized.description());
        entity.setGitRepositoryUrl(normalized.gitRepositoryUrl());
        entity.setBranch(normalized.branch());
        entity.setEnvironmentName(normalized.environment());
        entity.setDockerfilePath(normalized.dockerfilePath());
        entity.setContextPath(normalized.contextPath());
        entity.setContainerPort(normalized.containerPort());
    }

    private static Normalized normalize(ApplicationRequest request) {
        if (request == null) {
            throw ApiException.badRequest("Application is required");
        }
        String branch = request.branch() == null || request.branch().isBlank() ? "main" : request.branch();
        String description = request.description() == null || request.description().isBlank() ? null : request.description().trim();
        if (description != null && description.length() > 2000) {
            throw ApiException.badRequest("Description is too long");
        }
        return new Normalized(
                ResourcePolicies.appName(request.name()),
                description,
                ResourcePolicies.githubRepository(request.gitRepositoryUrl()),
                ResourcePolicies.branch(branch),
                parseEnvironment(request.environment()),
                ResourcePolicies.relativePath(request.dockerfilePath(), "Dockerfile"),
                ResourcePolicies.relativePath(request.contextPath(), "."),
                requirePort(request.containerPort())
        );
    }

    private static EnvironmentName parseEnvironment(String environment) {
        String value = environment == null || environment.isBlank() ? "DEV" : environment.trim();
        try {
            return EnvironmentName.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Environment must be DEV, STAGING, or PROD");
        }
    }

    private static ApplicationStatus parseOptionalStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ApplicationStatus.valueOf(status.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown application status");
        }
    }

    private static int requirePort(Integer containerPort) {
        if (containerPort == null || containerPort < 1 || containerPort > 65535) {
            throw ApiException.badRequest("Container port must be between 1 and 65535");
        }
        return containerPort;
    }

    private static ApplicationView toView(ApplicationEntity entity) {
        return new ApplicationView(
                entity.getId(),
                entity.getOwnerId(),
                entity.getName(),
                entity.getDescription(),
                entity.getGitRepositoryUrl(),
                entity.getBranch(),
                entity.getEnvironmentName(),
                entity.getDockerfilePath(),
                entity.getContextPath(),
                entity.getContainerPort(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static HistoryView toHistory(ApplicationHistoryEntity entity) {
        return new HistoryView(
                entity.getId(),
                entity.getApplicationId(),
                entity.getActorId(),
                entity.getAction(),
                entity.getSummary(),
                entity.getCreatedAt()
        );
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

    private record Normalized(
            String name,
            String description,
            String gitRepositoryUrl,
            String branch,
            EnvironmentName environment,
            String dockerfilePath,
            String contextPath,
            int containerPort
    ) {
    }
}
