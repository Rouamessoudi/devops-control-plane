create table deployments (
    id char(36) not null,
    application_id char(36) not null,
    owner_id char(36) not null,
    requested_by char(36) not null,
    image_repository varchar(255) null,
    image_tag varchar(64) null,
    replicas integer not null,
    environment_name varchar(16) not null,
    trigger_type varchar(16) not null,
    status varchar(16) not null,
    k8s_deployment_name varchar(255) null,
    k8s_revision bigint null,
    error_message varchar(1000) null,
    created_at datetime(6) not null,
    started_at datetime(6) null,
    finished_at datetime(6) null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create index idx_deployments_app_created on deployments (application_id, created_at);
create index idx_deployments_status on deployments (status);
create index idx_deployments_owner on deployments (owner_id);

create table deployment_events (
    id char(36) not null,
    deployment_id char(36) not null,
    actor_id char(36) null,
    action varchar(64) not null,
    message varchar(500) not null,
    created_at datetime(6) not null,
    primary key (id),
    constraint fk_deployment_events_deployment foreign key (deployment_id) references deployments (id) on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create index idx_deployment_events_deployment_created on deployment_events (deployment_id, created_at);
