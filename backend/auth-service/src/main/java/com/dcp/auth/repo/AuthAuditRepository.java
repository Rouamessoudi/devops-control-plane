package com.dcp.auth.repo;

import com.dcp.auth.domain.AuthAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuthAuditRepository extends JpaRepository<AuthAudit, UUID> {

    Page<AuthAudit> findByActorId(UUID actorId, Pageable pageable);
}
