CREATE TABLE pipelines (
    id CHAR(36) NOT NULL,
    application_id CHAR(36) NOT NULL,
    requested_by CHAR(36) NOT NULL,
    repository_url VARCHAR(512) NOT NULL,
    branch VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    github_delivery_id VARCHAR(80) NULL,
    conclusion VARCHAR(32) NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_pipelines_application (application_id),
    KEY idx_pipelines_requested_by (requested_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pipeline_steps (
    id CHAR(36) NOT NULL,
    pipeline_id CHAR(36) NOT NULL,
    step_order INT NOT NULL,
    name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    log_text MEDIUMTEXT NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_pipeline_steps_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipelines (id) ON DELETE CASCADE,
    KEY idx_pipeline_steps_pipeline (pipeline_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE webhook_events (
    id CHAR(36) NOT NULL,
    delivery_id VARCHAR(80) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    verified BIT(1) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_webhook_delivery (delivery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
