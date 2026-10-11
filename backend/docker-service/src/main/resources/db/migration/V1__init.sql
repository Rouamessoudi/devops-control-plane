CREATE TABLE docker_builds (
    id CHAR(36) NOT NULL,
    application_id CHAR(36) NOT NULL,
    requested_by CHAR(36) NOT NULL,
    repository_url VARCHAR(300) NOT NULL,
    branch VARCHAR(128) NOT NULL,
    image_name VARCHAR(255) NOT NULL,
    image_tag VARCHAR(64) NOT NULL,
    full_image VARCHAR(320) NOT NULL,
    status VARCHAR(32) NOT NULL,
    log_text MEDIUMTEXT NULL,
    error_message VARCHAR(1000) NULL,
    started_at TIMESTAMP(6) NULL,
    finished_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_docker_builds_application (application_id),
    KEY idx_docker_builds_requested_by (requested_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE docker_build_steps (
    id CHAR(36) NOT NULL,
    build_id CHAR(36) NOT NULL,
    step_order INT NOT NULL,
    name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    log_text MEDIUMTEXT NULL,
    PRIMARY KEY (id),
    KEY idx_docker_build_steps_build (build_id),
    CONSTRAINT fk_docker_build_steps_build FOREIGN KEY (build_id) REFERENCES docker_builds (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE managed_images (
    id CHAR(36) NOT NULL,
    build_id CHAR(36) NOT NULL,
    repository VARCHAR(255) NOT NULL,
    tag VARCHAR(64) NOT NULL,
    docker_image_id VARCHAR(128) NULL,
    size_bytes BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_managed_images_build (build_id),
    CONSTRAINT fk_managed_images_build FOREIGN KEY (build_id) REFERENCES docker_builds (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
