package com.dcp.dockerops;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DockerBuildStepRepository extends JpaRepository<DockerBuildStep, UUID> {

    List<DockerBuildStep> findByBuildIdOrderByStepOrderAsc(UUID buildId);
}
