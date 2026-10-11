package com.dcp.pipeline.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PipelineRepository extends JpaRepository<PipelineEntity, UUID> {

    @Query("select distinct p from PipelineEntity p left join fetch p.steps where p.id = :id")
    Optional<PipelineEntity> findWithStepsById(@Param("id") UUID id);

    Page<PipelineEntity> findByApplicationId(UUID applicationId, Pageable pageable);

    Page<PipelineEntity> findByRequestedBy(UUID requestedBy, Pageable pageable);

    Page<PipelineEntity> findByApplicationIdAndRequestedBy(UUID applicationId, UUID requestedBy, Pageable pageable);
}
