package com.dcp.deployment.repo;

import com.dcp.deployment.domain.DeploymentEntity;
import com.dcp.deployment.domain.DeploymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DeploymentRepository extends JpaRepository<DeploymentEntity, UUID> {

    Page<DeploymentEntity> findByOwnerId(UUID ownerId, Pageable pageable);

    Page<DeploymentEntity> findByOwnerIdAndApplicationId(UUID ownerId, UUID applicationId, Pageable pageable);

    Page<DeploymentEntity> findByApplicationId(UUID applicationId, Pageable pageable);

    List<DeploymentEntity> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    List<DeploymentEntity> findByApplicationIdAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(
            UUID applicationId, DeploymentStatus status, Instant createdAt);
}
