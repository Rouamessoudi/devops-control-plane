package com.dcp.application.repo;

import com.dcp.application.domain.AppEnvVarEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppEnvVarRepository extends JpaRepository<AppEnvVarEntity, UUID> {

    List<AppEnvVarEntity> findByApplicationIdOrderByEnvKeyAsc(UUID applicationId);

    Optional<AppEnvVarEntity> findByIdAndApplicationId(UUID id, UUID applicationId);

    Optional<AppEnvVarEntity> findByApplicationIdAndEnvKey(UUID applicationId, String envKey);
}
