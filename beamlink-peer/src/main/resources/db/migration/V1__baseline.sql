-- Full device schema as Hibernate ddl-auto create would build it now.
CREATE TABLE device (
    port integer not null,
    id bigint not null,
    device_id varchar(36),
    public_id varchar(36),
    device_name varchar(255) not null,
    auth_token varchar(255),
    ip_address varchar(255) not null,
    nexus_public_key TEXT,
    public_token varchar(255),
    state varchar(255) not null check (state in ('UNREGISTERED','PENDING_APPROVAL','APPROVED')),
    primary key (id)
);

CREATE TABLE batch_transfer (
    target_port integer,
    total_files integer not null,
    completed_at timestamp,
    created_at timestamp not null,
    total_size bigint not null,
    batch_transfer_id varchar(36) not null,
    dispatch_id varchar(36),
    source_device_id varchar(36) not null,
    target_device_id varchar(36),
    failure_reason varchar(255),
    paused_by varchar(255) check (paused_by in ('SOURCE','TARGET')),
    status varchar(255) not null check (status in ('PENDING','ACTIVE','PARTIAL','PAUSED','COMPLETED','CANCELLED','FAILED','EXPIRED')),
    target_ip varchar(255),
    primary key (batch_transfer_id)
);

CREATE TABLE directory_transfer (
    target_port integer,
    total_files integer not null,
    completed_at timestamp,
    created_at timestamp not null,
    total_size bigint not null,
    directory_transfer_id varchar(36) not null,
    dispatch_id varchar(36),
    source_device_id varchar(36) not null,
    target_device_id varchar(36),
    directory_name varchar(255) not null,
    failure_reason varchar(255),
    paused_by varchar(255) check (paused_by in ('SOURCE','TARGET')),
    source_path varchar(255),
    status varchar(255) not null check (status in ('PENDING','ACTIVE','PARTIAL','PAUSED','COMPLETED','CANCELLED','FAILED','EXPIRED')),
    target_ip varchar(255),
    primary key (directory_transfer_id)
);

CREATE TABLE directory_transfer_empty_dirs (
    directory_transfer_id varchar(36) not null,
    dir_path varchar(255)
);

CREATE TABLE file_transfer (
    max_retries integer not null,
    retry_count integer not null,
    source_port integer,
    speed_limit_mbps float,
    target_port integer,
    active_transfer_ms bigint not null,
    confirmed_offset bigint not null,
    created_at timestamp not null,
    expires_at timestamp,
    file_size bigint not null,
    last_chunk_at timestamp,
    batch_transfer_id varchar(36),
    directory_transfer_id varchar(36),
    source_device_id varchar(36) not null,
    target_device_id varchar(36),
    transfer_id varchar(36) not null,
    directory_name varchar(255),
    failure_reason varchar(255),
    file_name varchar(255) not null,
    file_path varchar(255),
    paused_by varchar(255) check (paused_by in ('SOURCE','TARGET')),
    relative_path varchar(255),
    source_ip varchar(255),
    status varchar(255) not null check (status in ('PENDING','ACTIVE','PAUSED','COMPLETED','CANCELLED','FAILED','EXPIRED')),
    target_ip varchar(255),
    primary key (transfer_id)
);

CREATE TABLE peers (
    online boolean not null,
    port integer not null,
    last_seen bigint not null,
    public_id blob,
    device_id blob not null,
    device_name varchar(255) not null,
    ip_address varchar(255) not null,
    primary key (device_id)
);

CREATE TABLE settings (
    updated_at timestamp not null,
    key varchar(255) not null,
    value varchar(255) not null,
    primary key (key)
);

CREATE TABLE transfer_logs (
    average_speed_mbps float not null,
    file_size bigint not null,
    timestamp timestamp not null,
    from_device_id blob,
    to_device_id blob,
    filename varchar(255) not null,
    from_device_name varchar(255),
    id blob not null,
    sync_state varchar(255) not null check (sync_state in ('PENDING','SYNCED','FAILED')),
    to_device_name varchar(255),
    primary key (id)
);
