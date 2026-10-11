package com.dcp.dockerops;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ManagedImageRepository extends JpaRepository<ManagedImage, UUID> {

    List<ManagedImage> findAllByOrderByCreatedAtDesc();
}
