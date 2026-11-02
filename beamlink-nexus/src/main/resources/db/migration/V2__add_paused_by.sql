ALTER TABLE file_transfer ADD COLUMN paused_by VARCHAR(20);
ALTER TABLE batch_transfer ADD COLUMN paused_by VARCHAR(20);
ALTER TABLE directory_transfer ADD COLUMN paused_by VARCHAR(20);
