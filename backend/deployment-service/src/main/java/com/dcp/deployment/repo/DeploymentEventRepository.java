package com.dcp.deployment.repo;

import com.dcp.deployment.domain.DeploymentEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DeploymentEventRepository extends JpaRepository<DeploymentEventEntity, UUID> {

    @Query("""
            select e from DeploymentEventEntity e
            where e.deploymentId in (select d.id from DeploymentEntity d where d.ownerId = :ownerId)
            """)
    Page<DeploymentEventEntity> findVisibleToOwner(@Param("ownerId") UUID ownerId, Pageable pageable);
}
