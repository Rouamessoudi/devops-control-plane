package com.dcp.application.repo;

import com.dcp.application.domain.ApplicationHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ApplicationHistoryRepository extends JpaRepository<ApplicationHistoryEntity, UUID> {

    List<ApplicationHistoryEntity> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    @Query("""
            select h from ApplicationHistoryEntity h
            where h.applicationId in (select a.id from ApplicationEntity a where a.ownerId = :ownerId)
            """)
    Page<ApplicationHistoryEntity> findVisibleToOwner(@Param("ownerId") UUID ownerId, Pageable pageable);
}
