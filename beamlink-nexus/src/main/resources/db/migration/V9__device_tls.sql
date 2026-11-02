ALTER TABLE registered_devices ADD COLUMN tls_fingerprint VARCHAR(64);
ALTER TABLE registered_devices ADD COLUMN tls_port INTEGER;
