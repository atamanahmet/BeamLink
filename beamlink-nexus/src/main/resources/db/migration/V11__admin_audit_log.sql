CREATE TABLE admin_audit_log (
    id               INTEGER     NOT NULL PRIMARY KEY AUTOINCREMENT,
    occurred_at      TIMESTAMP,
    event_type       TEXT        NOT NULL,
    outcome          TEXT        NOT NULL,
    source_address   TEXT,
    target_device_id  TEXT,
    detail           TEXT
);
