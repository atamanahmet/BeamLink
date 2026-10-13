CREATE TABLE registered_agents (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    name                VARCHAR(255),
    ip_address          VARCHAR(255),
    port                INTEGER         NOT NULL,
    state               VARCHAR(50)     NOT NULL,
    public_id           VARCHAR(36)     UNIQUE,
    registered_at       TIMESTAMP,
    requested_name      VARCHAR(255),
    rename_requested_at TIMESTAMP,
    approved_at         TIMESTAMP,
    last_seen_at        TIMESTAMP,
    approval_pushed     BOOLEAN         NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_agent_ip_port UNIQUE (ip_address, port)
);

CREATE TABLE nexus (
    id               INTEGER         NOT NULL PRIMARY KEY DEFAULT 1,
    nexus_id         VARCHAR(36)     NOT NULL UNIQUE,
    nexus_name       VARCHAR(255)    NOT NULL,
    ip_address       VARCHAR(255)    NOT NULL,
    port             INTEGER         NOT NULL DEFAULT 0,
    username         VARCHAR(255)    NOT NULL,
    encoded_password VARCHAR(255)    NOT NULL,
    public_token     VARCHAR(512)
                           CHECK (id = 1)
);

CREATE TABLE peer_list_version (
    id      INTEGER NOT NULL PRIMARY KEY DEFAULT 1,
    version BIGINT  NOT NULL DEFAULT 1,
    CHECK (id = 1)
);

CREATE TABLE pending_rename (
    id             VARCHAR(36)    NOT NULL PRIMARY KEY,
    agent_id       VARCHAR(36),
    current_name   VARCHAR(255),
    requested_name VARCHAR(255),
    requested_at   TIMESTAMP
);

CREATE TABLE transfer_logs (
    id                   VARCHAR(36)    NOT NULL PRIMARY KEY,
    from_agent_id        VARCHAR(36),
    from_agent_name      VARCHAR(255),
    to_agent_id          VARCHAR(36),
    to_agent_name        VARCHAR(255),
    filename             VARCHAR(255),
    file_size            BIGINT,
    average_speed_mbps   DOUBLE NOT NULL DEFAULT 0.0,
    transferred_at       TIMESTAMP
);

CREATE TABLE batch_transfer (
    batch_transfer_id VARCHAR(36)  NOT NULL PRIMARY KEY,
    dispatch_id VARCHAR(36),
    source_agent_id   VARCHAR(36)  NOT NULL,
    target_agent_id   VARCHAR(36),
    target_ip         VARCHAR(255),
    target_port       INTEGER,
    total_files       INTEGER      NOT NULL,
    total_size        BIGINT       NOT NULL,
    status            VARCHAR(50)  NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    completed_at      TIMESTAMP,
    failure_reason    VARCHAR(1024)
);

CREATE TABLE directory_transfer (
    directory_transfer_id VARCHAR(36)  NOT NULL PRIMARY KEY,
    dispatch_id           VARCHAR(36),
    source_agent_id       VARCHAR(36)  NOT NULL,
    target_agent_id       VARCHAR(36),
    target_ip             VARCHAR(255),
    target_port           INTEGER,
    directory_name        VARCHAR(255) NOT NULL,
    source_path           VARCHAR(1024),
    total_files           INTEGER      NOT NULL,
    total_size            BIGINT       NOT NULL,
    status                VARCHAR(50)  NOT NULL,
    created_at            TIMESTAMP    NOT NULL,
    completed_at          TIMESTAMP,
    failure_reason        VARCHAR(1024)
);

CREATE TABLE directory_transfer_empty_dirs (
    directory_transfer_id VARCHAR(36)  NOT NULL,
    dir_path              VARCHAR(1024),
    FOREIGN KEY (directory_transfer_id) REFERENCES directory_transfer (directory_transfer_id)
);

CREATE TABLE file_transfer (
    transfer_id             VARCHAR(36)   NOT NULL PRIMARY KEY,
    directory_transfer_id   VARCHAR(36),
    batch_transfer_id       VARCHAR(36),
    source_agent_id         VARCHAR(36)   NOT NULL,
    target_agent_id         VARCHAR(36),
    source_ip               VARCHAR(36),
    source_port             INTEGER,
    target_ip               VARCHAR(255),
    target_port             INTEGER,
    file_name               VARCHAR(255)  NOT NULL,
    file_path               VARCHAR(1024),
    file_size               BIGINT        NOT NULL,
    relative_path           VARCHAR(1024),
    directory_name          VARCHAR(255),
    confirmed_offset        BIGINT        NOT NULL DEFAULT 0,
    status                  VARCHAR(50)   NOT NULL,
    retry_count             INTEGER       NOT NULL DEFAULT 0,
    max_retries             INTEGER       NOT NULL DEFAULT 5,
    created_at              TIMESTAMP     NOT NULL,
    last_chunk_at           TIMESTAMP,
    active_transfer_ms      BIGINT NOT NULL DEFAULT 0,
    expires_at              TIMESTAMP,
    failure_reason          VARCHAR(1024)
);

CREATE TABLE settings (
    key        VARCHAR(255) NOT NULL PRIMARY KEY,
    value      TEXT         NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);