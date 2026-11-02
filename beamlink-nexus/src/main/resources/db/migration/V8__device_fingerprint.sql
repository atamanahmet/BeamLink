CREATE TABLE registered_devices_new (
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
    public_key          TEXT,
    fingerprint         VARCHAR(64)     UNIQUE
);

INSERT INTO registered_devices_new (
    id, name, ip_address, port, state, public_id, registered_at,
    requested_name, rename_requested_at, approved_at, last_seen_at
)
SELECT
    id, name, ip_address, port, state, public_id, registered_at,
    requested_name, rename_requested_at, approved_at, last_seen_at
FROM registered_devices;

DROP TABLE registered_devices;

ALTER TABLE registered_devices_new RENAME TO registered_devices;
