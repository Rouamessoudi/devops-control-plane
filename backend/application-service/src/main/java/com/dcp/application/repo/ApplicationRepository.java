package com.dcp.application.repo;

import com.dcp.application.domain.ApplicationEntity;
import com.dcp.application.domain.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {

    Page<ApplicationEntity> findByOwnerId(UUID ownerId, Pageable pageable);

    Page<ApplicationEntity> findByOwnerIdAndStatus(UUID ownerId, ApplicationStatus status, Pageable pageable);

    Page<ApplicationEntity> findByStatus(ApplicationStatus status, Pageable pageable);

    Optional<ApplicationEntity> findByOwnerIdAndName(UUID ownerId, String name);
}
