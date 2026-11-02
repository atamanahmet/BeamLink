-- Drop whole-file sha256; integrity is per-chunk now.
ALTER TABLE file_transfer DROP COLUMN sha256;
