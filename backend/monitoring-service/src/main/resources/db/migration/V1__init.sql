CREATE TABLE monitor_targets (
    id CHAR(36) NOT NULL,
    application_id CHAR(36) NOT NULL,
    owner_id CHAR(36) NOT NULL,
    deployment_name VARCHAR(63) NOT NULL,
    namespace_name VARCHAR(63) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_monitor_application (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
