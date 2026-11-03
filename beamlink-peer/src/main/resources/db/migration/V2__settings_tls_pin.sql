CREATE TABLE settings_new (
    updated_at timestamp not null,
    key varchar(255) not null,
    value varchar(255) not null,
    primary key (key)
);

INSERT INTO settings_new (updated_at, key, value) SELECT updated_at, key, value FROM settings;

DROP TABLE settings;

ALTER TABLE settings_new RENAME TO settings;
