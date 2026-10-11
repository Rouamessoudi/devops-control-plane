CREATE TABLE k8s_operations (
    id CHAR(36) NOT NULL,
    actor_id CHAR(36) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    resource_name VARCHAR(253) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_k8s_operations_actor (actor_id),
    KEY idx_k8s_operations_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
