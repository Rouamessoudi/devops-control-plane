package com.dcp.monitoring.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonitorTargetRepository extends JpaRepository<MonitorTargetEntity, UUID> {

    Optional<MonitorTargetEntity> findByApplicationId(UUID applicationId);

    List<MonitorTargetEntity> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<MonitorTargetEntity> findAllByOrderByCreatedAtDesc();
}
