create table applications (
    id char(36) not null,
    owner_id char(36) not null,
    name varchar(39) not null,
    description varchar(2000) null,
    git_repository_url varchar(512) not null,
    branch varchar(128) not null,
    environment_name varchar(16) not null,
    dockerfile_path varchar(200) not null,
    context_path varchar(200) not null,
    container_port integer not null,
    status varchar(16) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create unique index uk_applications_owner_name on applications (owner_id, name);
create index idx_applications_owner on applications (owner_id);
create index idx_applications_status on applications (status);

create table app_env_vars (
    id char(36) not null,
    application_id char(36) not null,
    env_key varchar(128) not null,
    value_ciphertext varchar(2000) not null,
    secret_flag bit(1) not null,
    created_at datetime(6) not null,
    primary key (id),
    constraint fk_app_env_vars_application foreign key (application_id) references applications (id) on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create unique index uk_app_env_vars_app_key on app_env_vars (application_id, env_key);

create table application_history (
    id char(36) not null,
    application_id char(36) not null,
    actor_id char(36) null,
    action varchar(64) not null,
    summary varchar(500) not null,
    created_at datetime(6) not null,
    primary key (id),
    constraint fk_application_history_application foreign key (application_id) references applications (id) on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create index idx_application_history_app_created on application_history (application_id, created_at);
