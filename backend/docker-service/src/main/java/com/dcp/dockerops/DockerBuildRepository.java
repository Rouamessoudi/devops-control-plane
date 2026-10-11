package com.dcp.dockerops;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DockerBuildRepository extends JpaRepository<DockerBuild, UUID> {

    List<DockerBuild> findByApplicationIdOrderByStartedAtDesc(UUID applicationId);

    List<DockerBuild> findByRequestedByOrderByStartedAtDesc(UUID requestedBy);

    List<DockerBuild> findByApplicationIdAndRequestedByOrderByStartedAtDesc(UUID applicationId, UUID requestedBy);

    List<DockerBuild> findAllByOrderByStartedAtDesc();
}
