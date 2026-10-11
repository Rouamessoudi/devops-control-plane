package com.dcp.kubernetes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface K8sOperationRepository extends JpaRepository<K8sOperation, UUID> {

    List<K8sOperation> findByActorIdOrderByCreatedAtDesc(UUID actorId);

    List<K8sOperation> findAllByOrderByCreatedAtDesc();
}
